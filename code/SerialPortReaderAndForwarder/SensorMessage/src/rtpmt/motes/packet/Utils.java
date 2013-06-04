/*
 * Utils class 
 * contains utility method used by the motes.packet package
 */
package rtpmt.motes.packet;

/**
 *
 * @author Kumar
 */
public class Utils {
    
    //compare two integer arrays and return boolean
    public static boolean compare(int[] first, int[] second){
        boolean isEqual = true;
        
        if( first.length > 0 && second.length > 0 && first.length ==  second.length){
            
            for (int i = 0; i < second.length; i++) {
                System.out.println(first[i] + "--" + second[i]);
                if(first[i] != second[i]){
                    isEqual = false;
                    break;
                }
            }
        }else{
            isEqual = false;
        }
        return isEqual;
    }
    
}
