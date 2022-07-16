package InputValidator;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class emailValidator {

    public static String getMail(String emailStr) {
        Matcher m = Pattern.compile("[a-zA-Z0-9_.+-]+@[a-zA-Z0-9-]+\\.[a-zA-Z0-9-.]+").matcher(emailStr);
        String res = null;
        while (m.find()) {
            res = m.group();
        }
        return res;
    }

    public static boolean validate(String emailStr) {
        Matcher m = Pattern.compile("[a-zA-Z0-9_.+-]+@[a-zA-Z0-9-]+\\.[a-zA-Z0-9-.]+").matcher(emailStr);
        return m.find();
    }
}
