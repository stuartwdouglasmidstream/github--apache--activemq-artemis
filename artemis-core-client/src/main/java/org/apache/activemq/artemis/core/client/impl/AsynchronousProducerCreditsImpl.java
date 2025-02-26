/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.activemq.artemis.core.client.impl;

import org.apache.activemq.artemis.api.core.SimpleString;
import org.jboss.logging.Logger;

public class AsynchronousProducerCreditsImpl extends AbstractProducerCreditsImpl {

   private static final Logger logger = Logger.getLogger(AsynchronousProducerCreditsImpl.class);

   int balance;

   final ClientProducerFlowCallback callback;

   public AsynchronousProducerCreditsImpl(ClientSessionInternal session, SimpleString address, int windowSize,
                                          ClientProducerFlowCallback callback) {
      super(session, address, windowSize);
      balance = windowSize;
      this.callback = callback;
   }

   @Override
   protected synchronized void actualAcquire(int credits) {
      synchronized (this) {
         balance -= credits;
         if (logger.isDebugEnabled()) {
            logger.debugf("actualAcquire on address %s with credits=%s, balance=%s, callbackType=%s", address, credits, balance, callback.getClass());
         }
         if (balance <= 0) {
            callback.onCreditsFlow(true, this);
         }
      }

   }

   @Override
   public int getBalance() {
      return balance;
   }

   @Override
   public void receiveCredits(int credits) {
      synchronized (this) {
         super.receiveCredits(credits);
         balance += credits;
         if (logger.isDebugEnabled()) {
            logger.debugf("receiveCredits with credits=%s, balance=%s, arriving=%s, callbackType=%s", credits, balance, arriving, callback.getClass());
         }
         callback.onCreditsFlow(balance <= 0, this);

         if (balance < 0 && arriving == 0) {
            // there are no more credits arriving and we are still negative, async large message send asked too much and we need to counter balance
            logger.debugf("Starve credits counter balance");
            int request = -balance + windowSize * 2;
            requestCredits(request);
         }
      }

   }


   @Override
   public void receiveFailCredits(final int credits) {
      super.receiveFailCredits(credits);
      if (logger.isDebugEnabled()) {
         logger.debugf("creditsFail %s, callback=%s", credits, callback.getClass());
      }
      callback.onCreditsFail(this);
   }

   @Override
   public void releaseOutstanding() {
      synchronized (this) {
         balance = 0;
         callback.onCreditsFlow(true, this);
         if (logger.isDebugEnabled()) {
            logger.debugf("releaseOutstanding credits, balance=%s, callback=%s", balance, callback.getClass());
         }
      }

   }
}
