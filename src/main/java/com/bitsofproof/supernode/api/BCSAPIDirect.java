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

import java.util.List;

public interface BCSAPIDirect
{
	public long getHeartbeat (long mine);

	/**
	 * get a block for the hash
	 * 
	 * @param hash
	 * @return block or null if hash is unknown
	 */
	public Block getBlock (String hash);

	/**
	 * get the transaction identified by the hash on the trunk
	 * 
	 * @param hash
	 * @return transaction or null if no transaction with that hash on the trunk
	 */
	public Transaction getTransaction (String hash);

	/**
	 * get the hash of the highest block on the trunk
	 * 
	 * @return
	 */
	public String getTrunk ();

	/**
	 * get transaction outputs that could be spent by the adresses
	 * 
	 * @param address
	 * @return list of outputs, eventually empty
	 */
	public List<TransactionOutput> getBalance (List<String> address);

	/**
	 * get account statement
	 * 
	 * @param addresses
	 * @param from
	 *            - unix time point the account statement should start from
	 * @return
	 */
	public AccountStatement getAccountStatement (List<String> addresses, long from);
}
