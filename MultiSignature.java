package demo;


import com.github.zeepin.ZPTSdk;
import com.github.zeepin.account.Account;
import com.github.zeepin.common.Address;
import com.github.zeepin.common.Helper;
import com.github.zeepin.core.transaction.Transaction;
import com.github.zeepin.smartcontract.Constract;

import java.io.BufferedReader;
import java.io.FileReader;
import java.util.ArrayList;
import java.util.List;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.apache.commons.cli.PosixParser;

public class MultiSignature {
	public static void main(String[] args) throws Exception {
		ZPTSdk zptSdk = getZptSdk();
		Options options = new Options();
		Option opt = new Option("pubkey", "public keys", true, "Put public keys");
		opt.setArgs(-2);
		opt.setRequired(false);
		options.addOption(opt);
		opt = new Option("n", "number", true, "Number of multi address");
		opt.setRequired(false);
		options.addOption(opt);
		opt = new Option("from", true, "From address");
		opt.setRequired(false);
		options.addOption(opt);
		opt = new Option("to", true, "To address");
		opt.setRequired(false);
		options.addOption(opt);
		opt = new Option("amount", true, "Amount");
		opt.setRequired(false);
		options.addOption(opt);
		opt = new Option("asset", true, "Asset Address, 1 - zpt , 2 - gala, 3 - fileread");
		opt.setRequired(false);
		options.addOption(opt);
		opt = new Option("w", true, "Wallet");
		opt.setRequired(false);
		options.addOption(opt);
		opt = new Option("addr", true, "Address");
		opt.setRequired(false);
		options.addOption(opt);
		opt = new Option("pwd", true, "Password");
		opt.setRequired(false);
		options.addOption(opt);
		opt = new Option("pk", "private key", true, "The private key using to signature");
		opt.setArgs(-2);
		opt.setRequired(false);
		options.addOption(opt);
		opt = new Option("sig", "signature data", true, "Transaction signature data");
		opt.setRequired(false);
		options.addOption(opt);
		opt = new Option("execute", true, "Execute transaction");
		opt.setRequired(false);
		options.addOption(opt);
		CommandLine commandLine = null;
		CommandLineParser parser = new PosixParser();
		HelpFormatter hf = new HelpFormatter();
		hf.setWidth(110);

		try {
			commandLine = parser.parse(options, args);
			if (commandLine.hasOption('h')) {
				hf.printHelp("testApp", options, true);
			}
			//生成新地址
			//-pubkey后面传公钥
			//-n传入最少签名人数
			String toAddr;
			if (commandLine.hasOption('n') && commandLine.hasOption("pubkey")) {
				int N = 0;
				toAddr = commandLine.getOptionValue('n');
				N = Integer.parseInt(toAddr);
				String[] pubkeys = commandLine.getOptionValues("pubkey");
				byte[][] pks = new byte[pubkeys.length][];

				for (int i = 0; i < pubkeys.length; ++i) {
					pks[i] = Helper.hexToBytes(pubkeys[i]);
				}

				Address newAddr = Address.addressFromMultiPubKeys(N, pks);
				System.out.println("-----------------------------------------------");
				System.out.println("New multi sign address is:" + newAddr.toBase58());
			}

			String fromAddr;
			int i;
			Transaction tx = null;
			int N;
			String[] pubkeys;
			byte[][] pks;
			String txNewHex;
			String sig;
			//构建交易，asset传入参数，zpt/gala/contract address
			if (commandLine.hasOption("from") && commandLine.hasOption("to") && commandLine.hasOption("amount") && commandLine.hasOption("asset"))
			{
				fromAddr = commandLine.getOptionValue("from");
				toAddr = commandLine.getOptionValue("to");
				long amount = Long.parseLong(commandLine.getOptionValue("amount"));
				if(commandLine.getOptionValue("asset").equals("zpt"))
					tx = zptSdk.nativevm().zpt().makeTransfer(fromAddr, toAddr, amount, fromAddr, 20000L, 1L);
				else if(commandLine.getOptionValue("asset").equals("gala"))
					tx = zptSdk.nativevm().gala().makeTransfer(fromAddr, toAddr, amount, fromAddr, 20000L, 1L);
				//合约
				else
				{
					try {
						String contractaddress = null;
						contractaddress = commandLine.getOptionValue("asset");

						List<String> inputarg = new ArrayList<>();
						inputarg.add(fromAddr); 
						inputarg.add(toAddr); 
						inputarg.add(multiply_10000(amount));
						//合约地址
						Constract constract = new Constract();
						constract.setMethod("transfer");
						constract.setConaddr(Helper.reverse(Helper.hexToBytes(contractaddress)));
						constract.setArgs(zptSdk.wasmvm().buildWasmContractJsonParam(inputarg.toArray()));
						tx = zptSdk.vm().makeInvokeCodeTransactionWasm(contractaddress, (String) null, constract.tobytes(), fromAddr,
								20000, 1);
					}catch(Exception e) {
						e.printStackTrace();
					}
				}
				System.out.println("-----------------------------------------------");
				System.out.println("transaction hex:" + tx.toHexString());
			}

			//私钥对交易签名
			//-pubkey里传公钥
			//-n传入最少签名数
			//-sig是transaction的16进制sig
			//-pk是16进制的私钥，这样-pk传过去的私钥就会对该transaction做签名
			if (commandLine.hasOption("pk") && commandLine.hasOption("sig") && commandLine.hasOption('n')
					&& commandLine.hasOption("pubkey")) {
				sig = commandLine.getOptionValue("sig");
				tx = Transaction.deserializeTxString(Helper.hexToBytes(sig));
				N = Integer.parseInt(commandLine.getOptionValue('n'));
				pubkeys = commandLine.getOptionValues("pubkey");
				pks = new byte[pubkeys.length][];

				for (i = 0; i < pubkeys.length; ++i) {
					pks[i] = Helper.hexToBytes(pubkeys[i]);
				}

				String[] privatekeys = commandLine.getOptionValues("pk");
				ArrayList<Account> acct = new ArrayList();

				
				for (i = 0; i < privatekeys.length; ++i) {
					acct.add(new Account(Helper.hexToBytes(privatekeys[i]), zptSdk.defaultSignScheme));
				}
				for (i = 0; i < acct.size(); ++i) {
					zptSdk.addMultiSign(tx, N, pks, (Account) acct.get(i));
				}
				txNewHex = tx.toHexString();
				System.out.println("-----------------------------------------------");
				System.out.println("tx sig data is:" + txNewHex);
				System.out.println("-----------------------------------------------");
				System.out.println("tx json:" + tx.sigs[0].json());
			}
			//钱包文件对交易签名
			//-w是钱包路径
			//-addr是其中一个地址
			//-pwd是密码
			if (commandLine.hasOption('w') && commandLine.hasOption("addr") && commandLine.hasOption("pwd")
					&& commandLine.hasOption("sig") && commandLine.hasOption('n') && commandLine.hasOption("pubkey")) {
				sig = commandLine.getOptionValue("sig");
				tx = Transaction.deserializeTxString(Helper.hexToBytes(sig));
				N = Integer.parseInt(commandLine.getOptionValue('n'));
				pubkeys = commandLine.getOptionValues("pubkey");
				pks = new byte[pubkeys.length][];

				for (i = 0; i < pubkeys.length; ++i) {
					pks[i] = Helper.hexToBytes(pubkeys[i]);
				}
				
				String path = commandLine.getOptionValue('w');
				zptSdk.openWalletFile(path);
				String addr = commandLine.getOptionValue("addr");
				txNewHex = commandLine.getOptionValue("pwd");
				Account acct0 = zptSdk.getWalletMgr().getAccount(addr, txNewHex);
				zptSdk.addMultiSign(tx, N, pks, acct0);
				String finalTX = tx.toHexString();
				System.out.println("-----------------------------------------------");
				System.out.println("tx sig data is:" + finalTX);
				System.out.println("-----------------------------------------------");
				System.out.println("tx json:" + tx.sigs[0].json());
			}
			
			//最后执行交易
			//-sig 传入签名过的交易
			//-execute 传入1
			//就会执行该交易，如果签名人数不够或者其他问题会弹出错误类型
			if (commandLine.hasOption("sig") && commandLine.hasOption("execute")) {
				sig = commandLine.getOptionValue("sig");
				if(commandLine.getOptionValue("execute").equals("1")) 
				{
					//tx = Transaction.deserializeFrom(Helper.hexToBytes(sig));
					Object obj = zptSdk.getConnect().sendRawTransaction(sig);
					System.out.println("-----------------------------------------------");
					System.out.println(obj);
				}else
					System.out.println("should not execute!");
			}
		} catch (ParseException var17) {
			hf.printHelp("testApp", options, true);
		} catch (Exception var18) {
			var18.printStackTrace();
		}

	}

	public static ZPTSdk getZptSdk() throws Exception {
		String ip = "http://test1.zeepin.net";
		String restUrl = ip + ":" + "20334";
		String rpcUrl = ip + ":" + "20336";
		String wsUrl = ip + ":" + "20335";
		ZPTSdk wm = ZPTSdk.getInstance();
		wm.setRpc(rpcUrl);
		wm.setRestful(restUrl);
		wm.setDefaultConnect(wm.getRpc());
		return wm;
	}
	/**
	 * 数据精度处理
	 *
	 * @author Junjie
	 * @date 2018/9/5 18:26
	 * @return com.github.zeepin.ZPTSdk
	 */
	private static String multiply_10000(long value) {
		double value1 = value * 10000;
		java.text.NumberFormat nf = java.text.NumberFormat.getInstance();
		nf.setGroupingUsed(false);
		String valueNew = nf.format(value1);
		return valueNew;
	}

}
