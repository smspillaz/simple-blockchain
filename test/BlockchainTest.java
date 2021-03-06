import java.util.ArrayList;
import java.util.Collection;

import java.security.NoSuchAlgorithmException;
import java.security.InvalidKeyException;
import java.security.SignatureException;

import javax.xml.bind.DatatypeConverter;

import org.junit.BeforeClass;
import org.junit.AfterClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.junit.Assert.assertThat;

public class BlockchainTest extends TestBase {
  @Test
  public void testSerialiseToJSON() throws NoSuchAlgorithmException,
                                           Block.MiningException,
                                           InvalidKeyException,
                                           SignatureException {
    Blockchain chain = new Blockchain(problemDifficulty);
    BlockMiner miner = registerForCleanup(new BlockMiner(chain, problemDifficulty));
    miner.waitFor(
      miner.appendPayload(convenienceTransactionPayloadFromIntegerKeys(senderKeys.getPublic(),
                                                                       senderKeys.getPublic(),
                                                                       50,
                                                                       senderKeys.getPrivate()), null)
    );
    chain.serialise();
  }

  @Test
  public void testDeserialiseFromJSON() throws NoSuchAlgorithmException,
                                               Blockchain.IntegrityCheckFailedException,
                                               Block.MiningException,
                                               InvalidKeyException,
                                               SignatureException {
    Blockchain chain = new Blockchain(problemDifficulty);
    BlockMiner miner = registerForCleanup(new BlockMiner(chain, problemDifficulty));
    miner.waitFor(
      miner.appendPayload(convenienceTransactionPayloadFromIntegerKeys(senderKeys.getPublic(),
                                                                       senderKeys.getPublic(),
                                                                       50,
                                                                       senderKeys.getPrivate()), null)
    );
    Blockchain deserialised = Blockchain.deserialise(chain.serialise());

    assertThat(chain.tipHash(), equalTo(deserialised.tipHash()));
  }

  @Test
  public void testDeserialiseManyBlocksFromJSON() throws NoSuchAlgorithmException,
                                                         Blockchain.IntegrityCheckFailedException,
                                                         Blockchain.WalkFailedException,
                                                         Block.MiningException,
                                                         InvalidKeyException,
                                                         SignatureException {
    Blockchain chain = new Blockchain(problemDifficulty);
    BlockMiner miner = registerForCleanup(new BlockMiner(chain, problemDifficulty));
    AsynchronouslyMutableLedger ledger = new AsynchronouslyMutableLedger(chain, miner);
    ledger.appendSignedTransaction(convenienceTransactionFromIntegerKeys(senderKeys.getPublic(),
                                                                     senderKeys.getPublic(),
                                                                     50,
                                                                     senderKeys.getPrivate()));

    ledger.appendSignedTransaction(convenienceTransactionFromIntegerKeys(senderKeys.getPublic(),
                                                                   receiverKeys.getPublic(),
                                                                   20,
                                                                   senderKeys.getPrivate()));
    ledger.appendSignedTransaction(convenienceTransactionFromIntegerKeys(senderKeys.getPublic(),
                                                                   receiverKeys.getPublic(),
                                                                   10,
                                                                   senderKeys.getPrivate()));
    miner.waitFor(
      ledger.appendSignedTransaction(convenienceTransactionFromIntegerKeys(senderKeys.getPublic(),
                                                                           receiverKeys.getPublic(),
                                                                           10,
                                                                           senderKeys.getPrivate()))

    );
    Blockchain deserialised = Blockchain.deserialise(chain.serialise());

    assertThat(chain.tipHash(), equalTo(deserialised.tipHash()));
  }

  @Test(expected=Blockchain.IntegrityCheckFailedException.class)
  public void testIntegrityCheckFailsWhenModifyingHashes() throws NoSuchAlgorithmException,
                                                                  Blockchain.IntegrityCheckFailedException,
                                                                  Blockchain.WalkFailedException,
                                                                  Block.MiningException,
                                                                  InvalidKeyException,
                                                                  SignatureException {
    Blockchain chain = new Blockchain(problemDifficulty);
    BlockMiner miner = registerForCleanup(new BlockMiner(chain, problemDifficulty));
    miner.waitFor(
      miner.appendPayload(convenienceTransactionPayloadFromIntegerKeys(senderKeys.getPublic(),
                                                     senderKeys.getPublic(),
                                                     50,
                                                     senderKeys.getPrivate()))
    );
    chain.walk(new Blockchain.BlockEnumerator() {
        public void consume(int index, Block block) {
            /* Block here is mutable, so we can mess with its contents. Its
             * hash will stay as is and this should fail validation. In this
             * scenario a malicious chain makes another wallet the genesis
             * node */
            block.payload = Transaction.withMutations(block.payload, new Transaction.Mutator() {
                public void mutate(Transaction transaction) {
                    transaction.sPubKey = transaction.rPubKey;
                    transaction.rPubKey = transaction.sPubKey;
                }
            });
        }
    });

    /* This should throw an integrity check failure */
    Blockchain.deserialise(chain.serialise());
  }

  @Test(expected=Blockchain.IntegrityCheckFailedException.class)
  public void testIntegrityCheckFailedWhenBlockNotMined() throws NoSuchAlgorithmException,
                                                                 Blockchain.IntegrityCheckFailedException,
                                                                 Blockchain.WalkFailedException,
                                                                 Block.MiningException,
                                                                 InvalidKeyException,
                                                                 SignatureException {
    final Blockchain chain = new Blockchain(problemDifficulty);
    BlockMiner miner = registerForCleanup(new BlockMiner(chain, problemDifficulty));
    miner.waitFor(
      miner.appendPayload(convenienceTransactionPayloadFromIntegerKeys(senderKeys.getPublic(),
                                                                       senderKeys.getPublic(),
                                                                       50,
                                                                       senderKeys.getPrivate()), null)
    );
    chain.walk(new Blockchain.BlockEnumerator() {
        public void consume(int index, Block block) {
            /* Change the nonce to something that doesn't prove that we did
             * the work required to mine this block and then re-hash the block */
            block.nonce = block.nonce + 1;
            try {
              block.hash = block.computeContentHash(chain.parentBlockHash(index));
            } catch (NoSuchAlgorithmException e) {
              System.err.println(e.getMessage());
            }
        }
    });

    /* This should throw an integrity check failure */
    Blockchain.deserialise(chain.serialise());
  }

  @Test(expected=Blockchain.IntegrityCheckFailedException.class)
  public void testIntegrityCheckFailsWhenModifyingCenterHash() throws NoSuchAlgorithmException,
                                                                      Blockchain.IntegrityCheckFailedException,
                                                                      Blockchain.WalkFailedException,
                                                                      Block.MiningException,
                                                                      InvalidKeyException,
                                                                      SignatureException {
    Blockchain chain = new Blockchain(problemDifficulty);
    BlockMiner miner = registerForCleanup(new BlockMiner(chain, problemDifficulty));
    AsynchronouslyMutableLedger ledger = new AsynchronouslyMutableLedger(chain, miner);
    ledger.appendSignedTransaction(convenienceTransactionFromIntegerKeys(senderKeys.getPublic(),
                                                                         senderKeys.getPublic(),
                                                                         50,
                                                                         senderKeys.getPrivate()));
    ledger.appendSignedTransaction(convenienceTransactionFromIntegerKeys(senderKeys.getPublic(),
                                                                   receiverKeys.getPublic(),
                                                                   20,
                                                                   senderKeys.getPrivate()));
    ledger.appendSignedTransaction(convenienceTransactionFromIntegerKeys(senderKeys.getPublic(),
                                                                   receiverKeys.getPublic(),
                                                                   10,
                                                                   senderKeys.getPrivate()));
    miner.waitFor(
      ledger.appendSignedTransaction(convenienceTransactionFromIntegerKeys(senderKeys.getPublic(),
                                                                           receiverKeys.getPublic(),
                                                                           10,
                                                                           senderKeys.getPrivate()))
    );

    chain.walk(new Blockchain.BlockEnumerator() {
        public void consume(int index, Block block) {
            /* Be a little bit evil and only modify the second transaction */
            if (index == 1) {
                block.payload = SignedObject.withMutations(block.payload, new SignedObject.Mutator() {
                    public void mutate(SignedObject blob) {
                        blob.payload = Transaction.withMutations(blob.payload, new Transaction.Mutator() {
                            public void mutate(Transaction transaction) {
                                transaction.sPubKey = receiverKeys.getPublic().getEncoded();
                                transaction.rPubKey = senderKeys.getPublic().getEncoded();
                            }
                        });
                    }
                });
            }
        }
    });

    /* This should throw an integrity check failure */
    Blockchain.deserialise(chain.serialise());
  }
}
