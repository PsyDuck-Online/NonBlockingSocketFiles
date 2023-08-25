
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.DatagramChannel;
import java.nio.channels.FileChannel;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.util.Arrays;
import java.util.Iterator;
import java.util.Scanner;

/**
 *
 * @author david
 */
public class Client {

    private static final String path = "C:\\Users\\david\\OneDrive\\Documentos\\NetBeansProjects\\Examen REDES\\ArchivosCliente";

    public static void main(String[] args) {
        try {
            Scanner sc = new Scanner(System.in);
            int puerto = 1234;
            int puertoLocal = 9999;
            String host = "127.0.0.1";
            InetSocketAddress addr = new InetSocketAddress(host, puerto);

            // Creamos el canal de datagrama
            DatagramChannel client = DatagramChannel.open();
            client.bind(new InetSocketAddress(puertoLocal));

            // Lo configuramos no bloqueante
            client.configureBlocking(false);

            // Abrimos el selector
            Selector sel = Selector.open();

            // Registramos el socket
            client.register(sel, SelectionKey.OP_WRITE | SelectionKey.OP_READ);

            // Creamos el bytebuffer
            ByteBuffer b = ByteBuffer.allocate(2000);
            b.clear();

            // Variables archivo
            File f = null;
            FileOutputStream fos = null;
            FileChannel escritor = null;

            boolean flag = true;
            int peticion;
            while (true) {
                sel.select();
                Iterator<SelectionKey> it = sel.selectedKeys().iterator();

                while (it.hasNext()) {
                    SelectionKey k = it.next();
                    it.remove();

                    if (k.isWritable()) {
                        System.out.print("Peticion: ");
                        peticion = sc.nextInt();
                        if (peticion == 0) { // Solicitamos la lista de archivos
                            System.out.println("Solicitud lista");
                            // Creamos el paquete
                            PaqueteUDP p = new PaqueteUDP(peticion, -1, -1, null);

                            // Serializamos
                            ByteArrayOutputStream baos = new ByteArrayOutputStream();
                            ObjectOutputStream oos = new ObjectOutputStream(baos);
                            oos.writeObject(p);
                            oos.flush();

                            // Cargamos al buffer
                            b.clear();
                            b.put(baos.toByteArray());
                            b.flip();

                            // Enviamos y limpiamos
                            client.send(b, addr);
                            b.clear();

                            k.interestOps(SelectionKey.OP_READ);
                        } else if (peticion == 1) { // Solicitamos un archivo
                            System.out.print("Numero archivo: ");
                            int nFile = sc.nextInt();
                            // Hacemos el paquete
                            PaqueteUDP p = new PaqueteUDP(peticion, nFile, -1, null);

                            // Serializamos
                            ByteArrayOutputStream baos = new ByteArrayOutputStream();
                            ObjectOutputStream oos = new ObjectOutputStream(baos);
                            oos.writeObject(p);
                            oos.flush();

                            // Cargamos al buffer
                            b.put(baos.toByteArray());
                            b.flip();

                            // Enviamos y limpiamos
                            client.send(b, addr);
                            b.clear();

                            k.interestOps(SelectionKey.OP_READ);
                        }
                    }

                    if (k.isReadable()) {
                        // Obtenemos el paquete
                        client.receive(b);
                        b.flip();

                        // Des-serialziamos y limpiamos
                        ByteArrayInputStream bais = new ByteArrayInputStream(b.array());
                        ObjectInputStream oos = new ObjectInputStream(bais);
                        PaqueteUDP p = (PaqueteUDP) oos.readObject();
                        oos.close();
                        bais.close();
                        b.clear();

                        // Obtenemos la peticion
                        int tmpPeticion = p.peticion;
                        //System.out.println("Peticion recibida: " + tmpPeticion);
                        if (tmpPeticion == 0) { // Devolvieron la lista de archivos
                            System.out.println("Lista recibida");
                            String listaString = new String(p.data, 0, p.data.length);
                            System.out.print("\n" + listaString);
                            k.interestOps(SelectionKey.OP_WRITE);
                        } else if (tmpPeticion == 1) { // archivo
                            //System.out.println(p.estado);
                            if (p.estado == 1) { // Meta datos
                                String nombre = new String(p.data, 0, p.data.length);
                                f = new File(path + "\\" + nombre);
                                System.out.println(nombre);
                                fos = new FileOutputStream(f);
                                escritor = fos.getChannel();
                                k.interestOps(SelectionKey.OP_READ);
                            } else if (p.estado == 2) { // contenido
                                System.out.println("Contenido");
                                ByteBuffer b2 = ByteBuffer.wrap(p.data);

                                escritor.write(b2);
                                k.interestOps(SelectionKey.OP_READ);
                            } else if (p.estado == 3) {
                                System.out.println("EOF");
                                escritor.close();
                                fos.close();
                                f = null;
                                k.interestOps(SelectionKey.OP_WRITE);
                            }

                        }

                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
