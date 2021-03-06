/*
 * Copyright 2012 Tamas Blummer tamas@bitsofproof.com
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.bitsofproof.supernode.api;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigInteger;
import java.security.SecureRandom;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;

import org.bouncycastle.asn1.ASN1InputStream;
import org.bouncycastle.asn1.ASN1Integer;
import org.bouncycastle.asn1.DERBitString;
import org.bouncycastle.asn1.DERInteger;
import org.bouncycastle.asn1.DEROctetString;
import org.bouncycastle.asn1.DERSequenceGenerator;
import org.bouncycastle.asn1.DERTaggedObject;
import org.bouncycastle.asn1.DLSequence;
import org.bouncycastle.asn1.sec.SECNamedCurves;
import org.bouncycastle.asn1.x9.X9ECParameters;
import org.bouncycastle.crypto.AsymmetricCipherKeyPair;
import org.bouncycastle.crypto.generators.ECKeyPairGenerator;
import org.bouncycastle.crypto.params.ECDomainParameters;
import org.bouncycastle.crypto.params.ECKeyGenerationParameters;
import org.bouncycastle.crypto.params.ECPrivateKeyParameters;
import org.bouncycastle.crypto.params.ECPublicKeyParameters;
import org.bouncycastle.crypto.signers.ECDSASigner;

public class ECKeyPair
{
	private static final SecureRandom secureRandom = new SecureRandom ();
	private static final X9ECParameters curve = SECNamedCurves.getByName ("secp256k1");
	private static final ECDomainParameters domain = new ECDomainParameters (curve.getCurve (), curve.getG (), curve.getN (), curve.getH ());

	private static final int signatureCacheLimit = 5000;
	private static final Set<String> validSignatures = new HashSet<String> ();

	private BigInteger priv;
	private byte[] pub;

	private ECKeyPair ()
	{
	}

	public static ECKeyPair createNew ()
	{
		ECKeyPairGenerator generator = new ECKeyPairGenerator ();
		ECKeyGenerationParameters keygenParams = new ECKeyGenerationParameters (domain, secureRandom);
		generator.init (keygenParams);
		AsymmetricCipherKeyPair keypair = generator.generateKeyPair ();
		ECPrivateKeyParameters privParams = (ECPrivateKeyParameters) keypair.getPrivate ();
		ECPublicKeyParameters pubParams = (ECPublicKeyParameters) keypair.getPublic ();
		ECKeyPair k = new ECKeyPair ();
		k.priv = privParams.getD ();
		k.pub = pubParams.getQ ().getEncoded ();
		return k;
	}

	public byte[] getPublic ()
	{
		if ( pub != null )
		{
			byte[] p = new byte[pub.length];
			System.arraycopy (pub, 0, p, 0, pub.length);
			return p;
		}
		return null;
	}

	public byte[] getAddress ()
	{
		return Hash.keyHash (pub);
	}

	public ECKeyPair (BigInteger priv)
	{
		this.priv = priv;
		pub = curve.getG ().multiply (priv).getEncoded ();
	}

	public ECKeyPair (byte[] store) throws ValidationException
	{
		ASN1InputStream s = new ASN1InputStream (store);
		try
		{
			DLSequence der = (DLSequence) s.readObject ();
			if ( !(((DERInteger) der.getObjectAt (0)).getValue ().equals (BigInteger.ONE)) )
			{
				throw new ValidationException ("wrong key version");
			}
			priv = new BigInteger (1, ((DEROctetString) der.getObjectAt (1).toASN1Primitive ()).getOctets ());
			s.close ();
			pub = curve.getG ().multiply (priv).getEncoded ();
		}
		catch ( IOException e )
		{
		}
		finally
		{
			try
			{
				s.close ();
			}
			catch ( IOException e )
			{
			}
		}
	}

	public byte[] toByteArray ()
	{
		ByteArrayOutputStream s = new ByteArrayOutputStream ();
		try
		{
			DERSequenceGenerator der = new DERSequenceGenerator (s);
			der.addObject (new ASN1Integer (1));
			der.addObject (new DEROctetString (priv.toByteArray ()));
			der.addObject (new DERTaggedObject (0, curve.toASN1Primitive ()));
			der.addObject (new DERTaggedObject (1, new DERBitString (pub)));
			der.close ();
		}
		catch ( IOException e )
		{
		}
		return s.toByteArray ();
	}

	public byte[] sign (byte[] hash) throws ValidationException
	{
		if ( priv == null )
		{
			throw new ValidationException ("Need private key to sign");
		}
		ECDSASigner signer = new ECDSASigner ();
		signer.init (true, new ECPrivateKeyParameters (priv, domain));
		BigInteger[] signature = signer.generateSignature (hash);
		ByteArrayOutputStream s = new ByteArrayOutputStream ();
		try
		{
			DERSequenceGenerator seq = new DERSequenceGenerator (s);
			seq.addObject (new DERInteger (signature[0]));
			seq.addObject (new DERInteger (signature[1]));
			seq.close ();
			return s.toByteArray ();
		}
		catch ( IOException e )
		{
		}
		return s.toByteArray ();
	}

	public static boolean verify (byte[] hash, byte[] signature, byte[] pub)
	{
		String cacheKey = ByteUtils.toHex (hash) + ":" + ByteUtils.toHex (signature) + ":" + ByteUtils.toHex (pub);
		synchronized ( validSignatures )
		{
			if ( validSignatures.contains (cacheKey) )
			{
				validSignatures.remove (cacheKey);
				return true;
			}
		}
		ASN1InputStream asn1 = new ASN1InputStream (signature);
		try
		{
			ECDSASigner signer = new ECDSASigner ();
			signer.init (false, new ECPublicKeyParameters (curve.getCurve ().decodePoint (pub), domain));

			DLSequence seq = (DLSequence) asn1.readObject ();
			BigInteger r = ((DERInteger) seq.getObjectAt (0)).getPositiveValue ();
			BigInteger s = ((DERInteger) seq.getObjectAt (1)).getPositiveValue ();
			asn1.close ();
			if ( signer.verifySignature (hash, r, s) )
			{
				synchronized ( validSignatures )
				{
					if ( validSignatures.size () >= signatureCacheLimit )
					{
						Iterator<String> i = validSignatures.iterator ();
						i.next ();
						i.remove ();
					}
					validSignatures.add (cacheKey);
				}
				return true;
			}
		}
		catch ( Exception e )
		{
			// threat format errors as invalid signatures
			return false;
		}
		finally
		{
			try
			{
				asn1.close ();
			}
			catch ( IOException e )
			{
			}
		}
		return false;
	}
}
