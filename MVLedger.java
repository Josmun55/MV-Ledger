
package com.jelurida.ardor.contracts;
//initialing the contract


import nxt.addons.AbstractContract;
import nxt.addons.*;
import nxt.addons.JA;
import nxt.addons.JO;
import nxt.blockchain.TransactionType;
import nxt.http.callers.*;
import nxt.http.responses.TransactionResponse;
import java.util.List;

import static nxt.blockchain.TransactionTypeEnum.CHILD_PAYMENT;
import static nxt.blockchain.TransactionTypeEnum.LEASING;


public class MVLedger<InvocationData, ReturnedData> extends AbstractContract<InvocationData, ReturnedData> {

    @ContractParametersProvider
    public interface Params {

        @ContractInvocationParameter
        int id();

        @ContractRunnerParameter
        JA secretPhrases();
    }


    @Override
    @ValidateContractRunnerIsRecipient
    @ValidateTransactionType(accept = {CHILD_PAYMENT})

    public JO processTransaction(TransactionContext context) {
        JO newOwner ;

        String ow_account = context.getAccount();   //mv owner account

            //Using the existing Lease renewal contract to fetch an existing account
        JO getAccountResponse = GetAccountCall.create().account(ow_account).includeLessors(true).call();

        JA ownersInfo = getAccountResponse.getArray("ownersInfo");
        List<String> ownersRS = getAccountResponse.getArray("ownersRS").values();

      //  List<String> allOwners = new ArrayList<>();

       // List<String> renewalCandidates = new ArrayList<>();

        Params params = context.getParams(Params.class);

        int leasingDelay = context.getBlockchainConstants().getInt("leasingDelay"); // load blockchain constant
        if (ownersInfo != null) {
            for (int i = 0; i < ownersInfo.size(); i++) {
                JO ownerInfo = ownersInfo.get(i);
                String ownerRS = ownersRS.get(i);
                int currentHeightTo = 0;
                if (ownerInfo.isExist("currentOwner") && ownerInfo.getString("currentOwner").equals(ow_account)) {
                    currentHeightTo = ownerInfo.getInt("currentHeightTo");
                }
                int nextHeightTo = 0;
                if (ownerInfo.isExist("newOwner") && ownerInfo.getString("newOwner").equals(ow_account)) {
                    nextHeightTo = ownerInfo.getInt("nextHeightTo");
                }
                int leaseTerminationHeight = Math.max(currentHeightTo, nextHeightTo);
                if (leaseTerminationHeight == 0) {
                    context.logInfoMessage("lessor %s has no lease (should never happen (I think))", ownerRS);
                    continue;
                }


                //  Storing the passphrase in the node contract runner configuration
                for (String secretPhrase : params.secretPhrases().values()) {
                    byte[] publicKey = context.getPublicKey(secretPhrase);
                    JO getAccountId = GetAccountIdCall.create().secretPhrase(secretPhrase).call();
                    if (getAccountId.isExist("errorCode")) {
                        context.logInfoMessage("Unable to find account with the public key %s", context.toHexString(publicKey));
                        continue;
                    }
                    String accountRS = context.rsAccount(getAccountId.getEntityId("account"));

                    // test that there is no lease in progress for which the stake hasn't matured yet
                    int blockTimeStamp = GetBlockCall.create().height(Math.max(context.getBlockchainHeight() - leasingDelay, 0)).getBlock().getTimestamp();
                    TransactionType leasingTransactionType = LEASING.getTransactionType();
                    List<TransactionResponse> previousTransactions = GetBlockchainTransactionsCall.create(context.getParentChain().getId()).
                            account(accountRS).type(leasingTransactionType.getType()).subtype(leasingTransactionType.getSubtype()).timestamp(blockTimeStamp).getTransactions();
                    if (previousTransactions.size() > 0) {
                        continue;
                    }
                    LeaseBalanceCall leaseBalanceCall = LeaseBalanceCall.create(context.getParentChain().getId())
                            .recipient(ow_account)
                            .secretPhrase(secretPhrase);
                    context.createTransaction(leaseBalanceCall);
                }

                //Sample details from the UI(Form)

                String Details = "{\"firstName\":\"M***\"," +
                        "\"lastName\":\"G***\"," +
                        "\"gender\":\"male\"," +
                        "\"email\":\"josmun@gmail.com\"," +
                        "\"mobileNo\":254712345," +
                        "\"ID\":12345," +
                        "\"Date\":\"2000-01-01T20:00:00.000Z\"," +
                        "\"countryId\":254," +
                        "\"country\":{\"name\":\"Kenya\"," +
                        "\"VehicleType\":Toyota," +
                        "\"VehicleXtics(YOM,model\":[]," +
                        "\"LicensePLate\":{\"name\":\"Approved\"," +
                        "\"Vehicledescription(mileage,color,...)\":[]," +
                        "\"OwnershipHistory\":[]}";


                //Parsing data obtained from the Reg form.

                newOwner = JO.parse(Details);

                JO MVvalue = new JO();

                MVvalue.put("firstName", newOwner.getString("firstName"));
                MVvalue.put("lastName", newOwner.getString("lastName"));
                MVvalue.put("email", newOwner.getString("email"));
                MVvalue.put("ID", newOwner.getInt("ID"));
                MVvalue.put("mobile", newOwner.getInt("mobile"));
                MVvalue.put("mobileNo", newOwner.getInt("mobileNo"));
                MVvalue.put("VehicleType", newOwner.getInt("VehicleType"));
                MVvalue.put("LicensePLate", newOwner.getInt("LicensePlate"));

                // Using setAccountproperty call to  update the blockchain
                SetAccountPropertyCall setAccountPropertyCall = SetAccountPropertyCall.create(context.getChainOfTransaction().getId()).
                        recipient(context.getSenderId()).
                        property("VehicleId").value(MVvalue.toJSONString());
                return context.createTransaction(setAccountPropertyCall);

            }
        }
        return context.getResponse();
    }

//Check duplicates
@Override
public <T extends TransactionResponse> boolean isDuplicate(T myTransaction, List<T> existingUnconfirmedTransactions) {
    if (super.isDuplicate(myTransaction, existingUnconfirmedTransactions)) {
        return true;
    }
    //isDuplicate will check for the duplicates
    for (TransactionResponse transactionResponse : existingUnconfirmedTransactions) {
        // Quickly eliminate all the obvious differences
        if (transactionResponse.getChainId() != myTransaction.getChainId()) {
            continue;
        }
        if (transactionResponse.getType() != myTransaction.getType()) {
            continue;
        }
        if (transactionResponse.getSubType() != myTransaction.getSubType()) {
            continue;
        }
        if (transactionResponse.getSenderId() != myTransaction.getSenderId()) {
            continue;
        }
        if (transactionResponse.getRecipientId() != myTransaction.getRecipientId()) {
            continue;
        }
    }
    return false;
}

}


