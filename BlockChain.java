import java.util.ArrayList;

// The BlockChain class should maintain only limited block nodes to satisfy the functionality.
// You should not have all the blocks added to the block chain in memory 
// as it would cause a memory overflow.


/*
“I acknowledge that I am aware of the academic integrity guidelines of this
course, and that I worked on this assignment independently without any
unauthorized help with coding or testing.” - Mariem Mostafa Mahmoud
 */


public class BlockChain {
    class Node {
        Block block;
        ArrayList<Node> children;
        UTXOPool pool = new UTXOPool();
        Node parent = null;

        Node(Block block, UTXOPool pool) {
            this.block = block;
            this.pool = pool;
            this.children = new ArrayList<Node>();
        }

        void setParent(Node parent) {
            this.parent = parent;
        }
        public Node getParent() {
            return parent;
        }

        public void insertChild(Node child){
            child.setParent(this);
            this.children.add(child);
        }

        public ArrayList<Node> getChildren() {
            return children;
        }

        public boolean isLeaf() {
            return this.children.size() == 0;
        }    
    }

    public static ArrayList<Node> getLeafNodes(ArrayList<Node> roots){
        ArrayList<Node> leafNodes = new ArrayList<Node>();
        for(int i = 0; i < roots.size(); i++){
            if(roots.get(i).isLeaf()){
                leafNodes.add(roots.get(i));
            }
            ArrayList <Node> root = roots.get(i).getChildren();
            leafNodes.addAll(getLeafNodes(root));
        }
        return leafNodes;
    }

    public static Node getLeafNode(ArrayList<Node> roots){
        ArrayList<Node> leafNodes = getLeafNodes(roots);
        Node maxLeaf = leafNodes.get(0);
        for(int i = 0; i < leafNodes.size(); i++){
            Node leaf = leafNodes.get(i);
            if(getHeight(leaf) > getHeight(maxLeaf)){
                maxLeaf = leaf;
            }
        }
        return maxLeaf;
    }


    public static int getMaxDepth(ArrayList <Node> roots){
        int maxDepth = -1;
        for(int i = 0; i < roots.size(); i++){
            ArrayList <Node> children = new ArrayList <Node>();
            children.addAll(roots.get(i).getChildren());
            int depth = getMaxDepth(children);
            if(depth > maxDepth){
                maxDepth = depth;
            }
        }
        return maxDepth + 1;
    }

    public static int getHeight(Node n){
        int height = 0;
        if(n.getParent() != null){
            height = getHeight(n.parent);}
        return height + 1;
    }
   
    public static ArrayList<Node> getNodes(ArrayList<Node> roots){
        ArrayList<Node> nodes = new ArrayList<Node>();
        for(int i = 0; i < roots.size(); i++){
            nodes.add(roots.get(i));
            ArrayList<Node> children = roots.get(i).getChildren();
            nodes.addAll(getNodes(children));
        }
        
        return nodes;
    }


    public static final int CUT_OFF_AGE = 10;

    /**
     * create an empty blockchain with just a genesis block. Assume {@code genesisBlock} is a valid
     * block
     */
    TransactionPool TxPool;
    ArrayList <Node> roots = new ArrayList<Node>();

    public BlockChain(Block genesisBlock) {
        UTXOPool utxoPool = new UTXOPool();
        Node root = new Node(genesisBlock,utxoPool);
        TxPool = new TransactionPool();
        Transaction coinbase = genesisBlock.getCoinbase();
        roots.add(root);

        for (int i = 0; i < coinbase.numOutputs(); i++) {
            UTXO utxo = new UTXO(coinbase.getHash(), i);
            utxoPool.addUTXO(utxo, coinbase.getOutput(i));
        }
    }

    /** Get the maximum height block */
    public Block getMaxHeightBlock() {
        return getLeafNode(roots).block;
    }

    /** Get the UTXOPool for mining a new block on top of max height block */
    public UTXOPool getMaxHeightUTXOPool() {
        return getLeafNode(roots).pool;
    }

    /** Get the transaction pool to mine a new block */
    public TransactionPool getTransactionPool() {
        return TxPool;
    }

    /**
     * Add {@code block} to the blockchain if it is valid. For validity, all transactions should be
     * valid and block should be at {@code height > (maxHeight - CUT_OFF_AGE)}, where maxHeight is 
     * the current height of the blockchain.
	 * <p>
	 * Assume the Genesis block is at height 1.
     * For example, you can try creating a new block over the genesis block (i.e. create a block at 
	 * height 2) if the current blockchain height is less than or equal to CUT_OFF_AGE + 1. As soon as
	 * the current blockchain height exceeds CUT_OFF_AGE + 1, you cannot create a new block at height 2.
     * 
     * @return true if block is successfully added
     */
    public boolean addBlock(Block block) {
        byte[] prevHash = block.getPrevBlockHash();
        ArrayList<Node> Nodes = getNodes(roots);
        Node newNode;
        boolean flag = false;

        for(Transaction t : block.getTransactions())
            TxPool.addTransaction(t);
        TxPool.addTransaction(block.getCoinbase());

        for(Node n : Nodes){
            if(n.block.getHash() == prevHash){
                flag = true;

                // check if the block is valid
                if(getHeight(n) + 1 > getMaxDepth(roots) - CUT_OFF_AGE){
                    UTXOPool clone = new UTXOPool();
                    clone = n.pool;
                    TxHandler handler = new TxHandler(clone);
                    Transaction[] txs = block.getTransactions().toArray(new Transaction[0]);
                    Transaction[] acceptedTxs = handler.handleTxs(txs);

                    //  if all transactions are valid, add the new block to the blockchain
                    if(block.getTransactions().size() == acceptedTxs.length){
                        UTXOPool pool = handler.getUTXOPool();

                        // add coinbase transaction to the pool
                        Transaction coinbase = block.getCoinbase();
                        for (int i = 0; i < coinbase.numOutputs(); i++) {
                            UTXO utxo = new UTXO(coinbase.getHash(), i);
                            pool.addUTXO(utxo, coinbase.getOutput(i));
                        }
                        newNode = new Node(block, pool);    
                        n.insertChild(newNode);
                        for (int i = 0; i < acceptedTxs.length; i++) {
                            TxPool.removeTransaction(acceptedTxs[i].getHash());
                        }
                    }
                    else
                        return false;
                }
                else
                    return false;
                break;
            }
        }
        if(!flag){
            return false;
        }

        if (getMaxDepth(roots) > CUT_OFF_AGE + 1){
                ArrayList<Node> children = new ArrayList<Node>();

                for(int i = 0; i < roots.size(); i++){

                    if(getHeight(roots.get(i)) < getMaxDepth(roots) - CUT_OFF_AGE ){
                        children.addAll(roots.get(i).getChildren());
                        for(int j = 0; j < children.size(); j++){
                            children.get(j).parent = null;
                            roots.get(i).children.get(j).parent = null;
                        }
                        roots.addAll(children);
                        roots.remove(i);
                        System.gc();       
                    }
                }                     
        }
        return true;
    }

    /** Add a transaction to the transaction pool */
    public void addTransaction(Transaction tx) {
        TxPool.addTransaction(tx);
    }
}