package gov.nist.blockmatrixtimestamped;

import java.security.MessageDigest;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.Signature;

public class SecurityUtil {
    /**
     * Calculate SHA-256 hash of the given input byte array.
     *
     * @param input data byte array
     * @return byte array of size 32 containing the hash
     * @throws RuntimeException if no SHA-256 algorithm found
     */
    public static byte[] applySha256(byte[] input) throws RuntimeException {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return digest.digest(input);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Apply ECDSA signature to the input data using the provided private key.
     *
     * @param privateKey private key to sign the data
     * @param input      input byte array
     * @return byte array with signature
     * @throws RuntimeException if no ECDSA algorithm was found, the key is invalid or some exception was thrown when
     *                          generating signature
     */
    public static byte[] applyECDSASig(PrivateKey privateKey, byte[] input) throws RuntimeException {
        try {
            Signature signer = Signature.getInstance("ECDSA");
            signer.initSign(privateKey);
            signer.update(input);
            signer.update(signer.getAlgorithm().getBytes());
            return signer.sign();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Verify ECDSA signature with the given data and public key.
     *
     * @param publicKey public key
     * @param data      data byte array
     * @param signature signature byte array
     * @return whether the signature is authentic
     * @throws RuntimeException if no ECDSA algorithm was found, the key is invalid or some exception was thrown when
     *                          verifying signature
     */
    public static boolean verifyECDSASig(PublicKey publicKey, byte[] data, byte[] signature) throws RuntimeException {
        try {
            Signature verifier = Signature.getInstance("ECDSA");
            verifier.initVerify(publicKey);
            verifier.update(data);
            verifier.update(verifier.getAlgorithm().getBytes());
            return verifier.verify(signature);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Return a string hexadecimal representation of the byte array.
     *
     * @param bytes input byte array
     * @return string hexadecimal representation
     */
    public static String bytesToHex(byte[] bytes) {
        final char[] HEX_ARRAY = "0123456789ABCDEF".toCharArray();

        char[] hexChars = new char[bytes.length * 2];
        for (int j = 0; j < bytes.length; j++) {
            int v = bytes[j] & 0xFF;
            hexChars[j * 2] = HEX_ARRAY[v >>> 4];
            hexChars[j * 2 + 1] = HEX_ARRAY[v & 0x0F];
        }

        return new String(hexChars);
    }
}
