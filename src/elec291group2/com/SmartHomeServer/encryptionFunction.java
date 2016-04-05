package elec291group2.com.SmartHomeServer;

public class encryptionFunction
{
	static private String[] keys =  
        {"ke", "vI", "ca", "da", "la", "HA", "HS", "8*", "-o", "8^",
         "Ke", "vP", "cA", "#a", "~a", "]A", ",S", ".*", "-O", "8?",
         "kE", "v%", "cy", "dz", "lc", "HU", "H|", "8)", "Po", "8-",
         "TR", "1I", "3a", "4a", "09a", "HsA"};
 
 static String message_id = "elec291project2";
 
 //for password login
 //length of the password has to be greater or equal to 6
 static public String password_hash(String input){
     int password_length = input.length();
//     StringBuffer to_return = new StringBuffer();
     String to_return = "";
     assert(password_length<100);
     //first layer
     String swap1 = swap(input, 0, password_length-1);
     String swap2 = swap(swap1, 1, password_length-2);
     String swap3 = swap(swap2, 2, password_length-3);
 
     //second layer
     char[] to_map = swap3.toCharArray();
     for (int i = 0; i < password_length; i++){
         int ascii_value = (int) to_map[i];
         if (ascii_value >= 97 && ascii_value <= 122){
//             to_return.append(keys[ascii_value-97]);
             to_return += keys[ascii_value-97];
         }
         else if(ascii_value >= 48 && ascii_value <= 57){
//             to_return.append(keys[ascii_value-48+26]);
             to_return += keys[ascii_value-48+26];
         }
     }
     
     return to_return.toString();  
 }
 
 //messages have to share the same length
 static public String encrypt_message(String message){
     StringBuffer to_return = new StringBuffer();
     to_return.append(message_id);
     to_return.append(message);
     return to_return.toString();
 }
 
 static public String decrypt_message(String message){
     String useful_info = message.replace("elec291project2", "");
     return useful_info;
 }
 
 //swap the characters inside a string
 static private String swap(String s, int p1, int p2){
     char[] to_swap = s.toCharArray();
     char temp = to_swap[p1];
     to_swap[p1] = to_swap[p2];
     to_swap[p2] = temp;
     return  new String(to_swap);
 }
}
