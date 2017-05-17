import java.io.IOException;
import java.security.cert.CertificateException;
import java.security.KeyStoreException;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.security.UnrecoverableKeyException;

import javafx.application.Platform;

import org.kohsuke.args4j.Argument;
import org.kohsuke.args4j.CmdLineException;
import org.kohsuke.args4j.CmdLineParser;
import org.kohsuke.args4j.Option;

public class WalletCLI {
    public static class Arguments {
        @Option(name="-keystore", usage="The Client-Side Java KeyStore to use (mandatory)", metaVar="KEYSTORE")
        public String keystore;

        @Option(name="-host", usage="The blockchain host to connect to (mandatory)", metaVar="HOST")
        public String host;

        public Arguments(String args[]) {
            CmdLineParser parser = new CmdLineParser(this);

            try {
                parser.parseArgument(args);

                if (keystore == null) {
                    throw new CmdLineException(parser, "Must provide a -keystore");
                }

                if (host == null) {
                    throw new CmdLineException(parser, "Must provide a -host");
                }

                if (System.getenv("KEYSTORE_PASSWORD") == null) {
                    throw new CmdLineException(parser, "Must set KEYSTORE_PASSWORD in the environment");
                }
            } catch (CmdLineException e) {
                System.err.println(e.getMessage());
                parser.printUsage(System.err);
                Platform.exit();
            }
        }
    }

    public static void main(String[] args) throws IOException,
                                                  CertificateException,
                                                  NoSuchAlgorithmException,
                                                  KeyStoreException,
                                                  KeyManagementException,
                                                  UnrecoverableKeyException,
                                                  Blockchain.WalkFailedException,
                                                  Blockchain.IntegrityCheckFailedException {
        Arguments arguments = new Arguments(args);

        /* In the case of the commandline interface, if something fails, we
         * just throw an exception and let it propogate. */
        WalletOrchestrator walletOrchestrator = new WalletOrchestrator(arguments.host,
                                                                       arguments.keystore,
                                                                       System.getenv("KEYSTORE_PASSWORD"));

        System.out.println("Current balance: " + walletOrchestrator.ascertainBalance(0));
        System.out.println(walletOrchestrator.transaction());
    }
}
