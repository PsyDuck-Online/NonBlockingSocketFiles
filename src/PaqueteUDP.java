
import java.io.Serializable;

/**
 *
 * @author david
 */
public class PaqueteUDP implements Serializable {

    int peticion;
    int estado;
    long tam;
    byte[] data;

    public PaqueteUDP(int peticion, int estado, long tam, byte[] data) {
        this.peticion = peticion;
        this.estado = estado;
        this.tam = tam;
        this.data = data;
    }

}
