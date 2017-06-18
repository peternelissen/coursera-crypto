import java.util.ArrayList;

public class TxHandler {

	private UTXOPool utxoPool;
	private ArrayList<UTXO> lastClaimedUtxos = new ArrayList<UTXO>();
	
	/**
	 * Creates a public ledger whose current UTXOPool (collection of unspent
	 * transaction outputs) is {@code utxoPool}. This should make a copy of
	 * utxoPool by using the UTXOPool(UTXOPool uPool) constructor.
	 */
	public TxHandler(UTXOPool utxoPool) {
		this.utxoPool = new UTXOPool(utxoPool);
	}

	/**
	 * @return true if: (1) all outputs claimed by {@code tx} are in the current
	 *         UTXO pool, (2) the signatures on each input of {@code tx} are
	 *         valid, (3) no UTXO is claimed multiple times by {@code tx}, (4)
	 *         all of {@code tx}s output values are non-negative, and (5) the
	 *         sum of {@code tx}s input values is greater than or equal to the
	 *         sum of its output values; and false otherwise.
	 */
	public boolean isValidTx(Transaction tx) {
		// reset
		lastClaimedUtxos = new ArrayList<UTXO>();
		
		// loop over inputs
		ArrayList<Transaction.Input> inputs = tx.getInputs();
		double totalInput = 0;
		for (int i = 0; i < inputs.size(); i++) {
			Transaction.Input input = inputs.get(i);
			System.out.println("Processing input " + input.prevTxHash + " with index " + input.outputIndex);
			
			// search the referenced output in the utxoPool
			UTXO utxo = new UTXO(input.prevTxHash, input.outputIndex);
			Transaction.Output txOutput = utxoPool.getTxOutput(utxo);
			
			System.out.println("Validating (1)");
			if (txOutput == null) {
				System.out.println("Cannot find output claimed in input");
				return false;
			} else {
				System.out.println("Found output claimed by input");
			}
			
			System.out.println("Validating (2)");
			if (Crypto.verifySignature(txOutput.address, tx.getRawDataToSign(i), input.signature)) {
				System.out.println("Signature valid");
			} else {
				System.out.println("Signature not valid");
				return false;
			}
			
			System.out.println("Validating (3)");
			if (lastClaimedUtxos.contains(utxo)) {
				System.out.println("UTXO claimed more than once.");
				return false;
			} else {
				System.out.println("UTXO added to claimedUtxos");
				lastClaimedUtxos.add(utxo);
			}
			
			totalInput += txOutput.value;
		}
		
		System.out.println("Validating (4)");
		ArrayList<Transaction.Output> outputs = tx.getOutputs();
		double totalOutput = 0;
		for (int i = 0; i < outputs.size(); i++) {
			Transaction.Output output = outputs.get(i);
			if (output.value < 0) {
				System.out.println("Found negative value in output: " + i);
				return false;
			}
			
			totalOutput += output.value;
		}
		
		System.out.println("Validating (5)");
		if (totalInput >= totalOutput) {
			System.out.println("total input bigger or equal to totalOutput");
		} else {
			System.out.println("total input smaller than totalOutput");
			return false;
		}
		
		return true;
	}

	/**
	 * Handles each epoch by receiving an unordered array of proposed
	 * transactions, checking each transaction for correctness, returning a
	 * mutually valid array of accepted transactions, and updating the current
	 * UTXO pool as appropriate.
	 */
	public Transaction[] handleTxs(Transaction[] possibleTxs) {
		ArrayList<Transaction> validTxs = new ArrayList<Transaction>();
		for (int i = 0; i < possibleTxs.length; i++) {
			Transaction transaction = possibleTxs[i];
			if (isValidTx(transaction)) {
				validTxs.add(transaction);
				transaction.finalize();
				
				// remove claimed utxos (saved by valid method)
				for (UTXO utxo : lastClaimedUtxos) {
					utxoPool.removeUTXO(utxo);
				}
				
				// add new UTXOs created by this transaction
				ArrayList<Transaction.Output> outputs = transaction.getOutputs();
				for (int j = 0; j < outputs.size(); j++) {
					Transaction.Output output = outputs.get(j);
					utxoPool.addUTXO(new UTXO(transaction.getHash(), j), output);
				}
				
			} else {
				System.out.println("Found invalid transaction: " + i + ". Not adding to validTxs");
				break;
			}
		}
		return (Transaction[]) validTxs.toArray();
	}

}
