
import java.security.PublicKey;
import java.util.ArrayList;
/*
“I acknowledge that I am aware of the academic integrity guidelines of this
course, and that I worked on this assignment independently without any
unauthorized help with coding or testing.” - Mariem Mostafa Mahmoud
 */

public class TxHandler {

    private UTXOPool utxoPool;

    /**
     * Creates a public ledger whose current UTXOPool (collection of unspent
     * transaction outputs) is
     * {@code utxoPool}.
     */
    public TxHandler(UTXOPool utxoPool) {
        this.utxoPool = new UTXOPool(utxoPool);
    }

    public UTXOPool getUTXOPool() {
        return utxoPool;
    }
    
    /**
     * @return true if:
     *         (1) all outputs claimed by {@code tx} are in the current UTXO pool,
     *         (2) the signatures on each input of {@code tx} are valid,
     *         (3) no UTXO is claimed multiple times by {@code tx},
     *         (4) all of {@code tx}s output values are non-negative, and
     *         (5) the sum of {@code tx}s input values is greater than or equal to
     *         the sum of its output
     *         values; and false otherwise.
     */

    public boolean isValidTx(Transaction tx) {
        ArrayList<UTXO> allUTXO = utxoPool.getAllUTXO(); // get all the UTXOs
        byte[] hash = tx.getHash(); // get the hash of the transaction
        boolean flag = true;
        
        double inputSum = 0;
        double outputSum = 0;
        double opval = 0;

        // iterate through all outputs of the transaction
        for (int i = 0; i < tx.numInputs(); i++) {
            byte[] prevhash = tx.getInput(i).prevTxHash;
            int index = tx.getInput(i).outputIndex;
            UTXO utxo = new UTXO(prevhash, index); // make a UTXO with the tx hash and the index

            // (1) all outputs claimed by {@code tx} are in the current UTXOpool
            flag = false;
            for (int j = 0; j < allUTXO.size(); j++) {// iterate through the pool to
                if (utxoPool.contains(utxo) == true) { // check if the pool contains the UTXO
                    flag = true;
                }
            }
            if (flag == false) // Do not continue if one output is not in the pool
                return false;


            // (2) the signatures on each input of {@code tx} are valid
            byte[] msg = tx.getRawDataToSign(i); // get the message
            byte[] sig = tx.getInput(i).signature; // get the signature of each input
            Transaction.Output previousTx = utxoPool.getTxOutput(utxo);
            // PublicKey pk = tx.getOutput(tx.getInput(i).outputIndex).address; // get the public key
            PublicKey pk = previousTx.address; // get the public key
            if (Crypto.verifySignature(pk, msg, sig) == false) {
                return false;
            }

            //get the value of each input
            opval = (utxoPool.getTxOutput(utxo)).value;
        }

        // (3) no UTXO is claimed multiple times by {@code tx}
        int count = 0;
        for (int i = 0; i < tx.numOutputs(); i++) { // iterate through outputs
            count = 0;// initialize the count
            UTXO utxo2 = new UTXO(hash, i); // make a UTXO with the tx hash and the index
            for (int j = 0; j < allUTXO.size(); j++) {// iterate through the pool to
                if (utxoPool.contains(utxo2) == true) { // check if the pool contains the UTXO
                    count++;
                }
            }
            if (count > 1)
               return false;


            // (4) all of {@code tx}s output values are non-negative
            if (tx.getOutput(i).value < 0) {
                return false;
            }

            outputSum += tx.getOutput(i).value;
            
        }
//(5) the sum of {@code tx}s input values is greater than or equal to the sum of its output values; and false otherwise.
        inputSum += opval;
        if(inputSum < outputSum)
            return false;

    return true;
    }

    /**
     * Handles each epoch by receiving an unordered array of proposed transactions,
     * checking each
     * transaction for correctness, returning a mutually valid array of accepted
     * transactions, and
     * updating the current UTXO pool as appropriate.
     */
    public Transaction[] handleTxs(Transaction[] possibleTxs) {
        ArrayList<Transaction> acceptedTxs = new ArrayList<Transaction>();
        ArrayList<Transaction> rejectedTxs = new ArrayList<Transaction>();

        for (int i = 0; i < possibleTxs.length; i++) {
            if (isValidTx(possibleTxs[i]) == true) {
                acceptedTxs.add(possibleTxs[i]);
                for (int j = 0; j < possibleTxs[i].numInputs(); j++) {
                    byte[] prevhash = possibleTxs[i].getInput(j).prevTxHash;
                    int index = possibleTxs[i].getInput(j).outputIndex;
                    UTXO prevUtxo = new UTXO(prevhash, index);
                    utxoPool.removeUTXO(prevUtxo);
                }
                for (int j = 0; j < possibleTxs[i].numOutputs(); j++) {
                    byte[] hash = possibleTxs[i].getHash();
                    UTXO utxo = new UTXO(hash, j);
                    utxoPool.addUTXO(utxo, possibleTxs[i].getOutput(j));
                } 
            }
            else 
                rejectedTxs.add(possibleTxs[i]);
        }

        int lengthOfRejectedOld = rejectedTxs.size() + 1;
        int lengthOfRejected = rejectedTxs.size();

        if(lengthOfRejected < lengthOfRejectedOld){
            lengthOfRejectedOld = lengthOfRejected;
            for(int i = 0; i < rejectedTxs.size(); i++) {
                if (isValidTx(rejectedTxs.get(i)) == true) {
                    acceptedTxs.add(rejectedTxs.get(i));

                    //add the new transaction to the pool
                    for (int j = 0; j < rejectedTxs.get(i).numOutputs(); j++) {
                        byte[] hash = rejectedTxs.get(i).getHash();
                        UTXO utxo = new UTXO(hash, j);
                        utxoPool.addUTXO(utxo, rejectedTxs.get(i).getOutput(j));
                    }

                    //remove the transacation that spent its output
                    for (int j = 0; j < rejectedTxs.get(i).numInputs(); j++) {
                        byte[] prevhash = rejectedTxs.get(i).getInput(j).prevTxHash;
                        int index = rejectedTxs.get(i).getInput(j).outputIndex;
                        UTXO prevUtxo = new UTXO(prevhash, index);
                        utxoPool.removeUTXO(prevUtxo);
                    }
                    rejectedTxs.remove(i);
                }
            }
        }
        Transaction[] acceptedTxArray = acceptedTxs.toArray(new Transaction[acceptedTxs.size()]);
        return acceptedTxArray;
    }

}
