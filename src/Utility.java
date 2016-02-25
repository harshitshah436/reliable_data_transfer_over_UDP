/* 
 * Utility.java
 * 
 * CSCI 651: Foundations of Computer Networks, Project 2. To implement RDT
 * protocol using UDP datagrams and TCP tahoe like congestion control.
 */
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.math.BigInteger;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * Utility class to perform binary operations, MD5 hash generation and file
 * operations.
 * 
 * @author Harshit
 */
public class Utility {
    /**
     * Make 16 bits of data
     * 
     * @param str
     * @return string array of 16bits word each.
     */
    public static String[] divideInto16bits(String str) {
        int len = (str.length() / 16);

        String array_16_bits[] = new String[len];
        for (int i = 0; i < len; i++) {
            array_16_bits[i] = str.substring(i * 16, (i + 1) * 16);
        }

        return array_16_bits;
    }

    /**
     * Calculate checksum.
     * 
     * @param array_16_bits
     *            String array
     * @return checksum string.
     */
    public static String calculateChecksum(String array_16_bits[]) {

        String result = "0000000000000000";

        for (int i = 0; i < array_16_bits.length; i++) {
            result = binaryAdditionOf16BitStrings(result, array_16_bits[i]);
        }

        // 1's complement of result
        char[] char_array = result.toCharArray();
        for (int i = 0; i < result.length(); i++) {
            char_array[i] = char_array[i] == '0' ? '1' : '0';
        }
        result = String.valueOf(char_array);

        return result;
    }

    /**
     * Binary addition of 16 bit strings.
     * 
     * @param string1
     * @param string2
     * @return addition of two 16 bit binary numbers.
     */
    public static String binaryAdditionOf16BitStrings(String string1,
            String string2) {
        char[] binary = string2.toCharArray();
        char[] temp_result = string1.toCharArray();
        char bit_result = '0';
        char carry = '0';
        for (int j = 15; j >= 0; j--) {
            bit_result = xor(carry, (xor(binary[j], temp_result[j])));
            carry = xor((char) (binary[j] & temp_result[j]),
                    ((char) (carry & (xor(binary[j], temp_result[j])))));
            temp_result[j] = bit_result;
        }
        for (int j = 15; j >= 0; j--) {
            if (carry == '1') {
                char sum = temp_result[j];
                temp_result[j] = xor(temp_result[j], carry);
                carry = (char) (sum & carry);
            }
        }
        // Convert char array to String.
        string1 = String.valueOf(temp_result);

        return string1;
    }

    /**
     * XOR operation between two chars
     * 
     * @param char1
     * @param char2
     * @return xor of two char
     */
    public static char xor(char char1, char char2) {
        return (char1 ^ char2) == 0 ? '0' : '1';
    }

    /**
     * Read file contents and convert into byte array.
     * 
     * @param file
     * @return byte array of file contents.
     */
    public static byte[] readFileIntoByteArray(File file) {

        ByteArrayOutputStream ous = null;
        InputStream ios = null;
        try {
            byte[] buffer = new byte[4096];
            ous = new ByteArrayOutputStream();
            ios = new FileInputStream(file);
            int read = 0;
            while ((read = ios.read(buffer)) != -1) {
                ous.write(buffer, 0, read);
            }

            ios.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
        return ous.toByteArray();
    }

    /**
     * Calculate MD5 hash of a given file.
     * 
     * @param file
     * @return MD5 hash as a String.
     */
    public static String calculateMD5hashOfFile(File file) {

        byte[] input = readFileIntoByteArray(file);
        MessageDigest md = null;
        try {
            md = MessageDigest.getInstance("MD5");
        } catch (NoSuchAlgorithmException e) {
            e.printStackTrace();
        }
        byte[] digest = md.digest(input);
        BigInteger bigInt = new BigInteger(1, digest);
        String hash = bigInt.toString(16);
        return hash;
    }
}
