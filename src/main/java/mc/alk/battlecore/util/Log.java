package mc.alk.battlecore.util;


public class Log {

    private static final String COLOR_MC_CHAR = Character.toString((char) 167);

    public static void info(String msg){
            System.out.println(colorChat(msg));
    }

    public static void warn(String msg){
            System.err.println(colorChat(msg));
    }

    public static void err(String msg){
            System.err.println(colorChat(msg));
    }

    public static String colorChat(String msg) {
        return msg.replaceAll("&", COLOR_MC_CHAR);
    }

    public static void debug(String string) {
        System.out.println(string);
    }
}
