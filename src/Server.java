
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
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
public class Server {

    public static void main(String[] args) {
        String path = "C:\\Users\\david\\OneDrive\\Documentos\\NetBeansProjects\\Examen REDES\\ArchivosServidor";
        int MTU = 2000;
        int puerto = 1234;
        Scanner sc = new Scanner(System.in);
        /*System.out.print("Introduce el puerto: ");
        puerto = sc.nextInt();
        System.out.print("Introduce el MTU(tamanio maximo de paquete): ");
        MTU = sc.nextInt();*/
        InetSocketAddress addrLocal = new InetSocketAddress(puerto);
        InetSocketAddress tmpAddr = null;
        try {
            DatagramChannel server = DatagramChannel.open();
            server.bind(addrLocal);
            server.configureBlocking(false);
            Selector sel = Selector.open();
            server.register(sel, SelectionKey.OP_READ | SelectionKey.OP_WRITE);

            // Obtenemos la lista de archivos en la carpeta
            File[] lista = getListaArchivos(path);

            // Variables de control
            FileInputStream fis = null;
            FileChannel lector = null;

            // Creamos el buffer
            ByteBuffer b = ByteBuffer.allocate(MTU);
            b.clear();

            File f = null;
            int op = -1;

            while (true) {
                sel.select();
                Iterator<SelectionKey> it = sel.selectedKeys().iterator();

                while (it.hasNext()) {
                    SelectionKey k = it.next();
                    it.remove();

                    if (k.isReadable()) {

                        // Recibimos el paquete
                        b.clear();
                        tmpAddr = (InetSocketAddress) server.receive(b);
                        b.flip();
                        // Des-serializamos y limpiamos
                        ByteArrayInputStream bais = new ByteArrayInputStream(b.array());
                        ObjectInputStream ois = new ObjectInputStream(bais);
                        PaqueteUDP p = (PaqueteUDP) ois.readObject();
                        ois.close();
                        bais.close();
                        b.clear();

                        // Obtenemos el tipo de peticion
                        int peticion = p.peticion;

                        if (peticion == 0) { // Enviamos una cadena con la lista de archivos
                            op = 0;
                            k.interestOps(SelectionKey.OP_WRITE);
                        } else if (peticion == 1) {
                            f = lista[p.estado];
                            fis = new FileInputStream(f);
                            lector = fis.getChannel();
                            op = 1;
                            k.interestOps(SelectionKey.OP_WRITE);
                        }
                        continue;
                    }

                    if (k.isWritable()) {
                        if (op == 0) {
                            System.out.println("Enviando lista");
                            String listaString = "";
                            for (int i = 0; i < lista.length; i++) {
                                listaString += i + ") " + lista[i].getName() + "\n";
                            }

                            // Creamos el paquete de respuesta
                            PaqueteUDP response = new PaqueteUDP(0, -1, -1, listaString.getBytes());

                            // Serializamos
                            ByteArrayOutputStream baos = new ByteArrayOutputStream();
                            ObjectOutputStream oos = new ObjectOutputStream(baos);
                            oos.writeObject(response);
                            oos.flush();
                            // cargamos en el buffer
                            b.put(baos.toByteArray());
                            b.flip();

                            // enviamos la respuesta
                            server.send(b, tmpAddr);
                            b.clear();
                            k.interestOps(SelectionKey.OP_READ);
                            op = -1;
                        } else if (op == 1) { // Envio de meta datos
                            System.out.println("Envio metadatos");
                            String nombre = f.getName();
                            long tam = f.length();
                            PaqueteUDP p = new PaqueteUDP(1, op, tam, nombre.getBytes());
                            ByteArrayOutputStream baos = new ByteArrayOutputStream();
                            ObjectOutputStream oos = new ObjectOutputStream(baos);
                            oos.writeObject(p);
                            oos.flush();

                            b.clear();
                            b.put(baos.toByteArray());
                            b.flip();

                            server.send(b, tmpAddr);
                            oos.close();
                            baos.close();
                            op = 2;
                            //System.out.println(op);
                            k.interestOps(SelectionKey.OP_WRITE);
                        } else if (op == 2) { // leemos datos
                            System.out.println("Envio de datos");
                            ByteBuffer b2 = ByteBuffer.allocate(MTU - 200);
                            int r = lector.read(b2);
                            b2.flip();
                            System.out.println(r);
                            if (r >= 0) {
                                PaqueteUDP p = new PaqueteUDP(1, op, f.length(), Arrays.copyOf(b2.array(), b2.limit()));
                                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                                ObjectOutputStream oos = new ObjectOutputStream(baos);
                                oos.writeObject(p);
                                oos.flush();

                                b.clear();
                                b.put(baos.toByteArray());
                                b.flip();

                                server.send(b, tmpAddr);
                                oos.close();
                                baos.close();
                                op = 2;

                            } else if (r == -1) { // EOF
                                op = 3;
                                PaqueteUDP p = new PaqueteUDP(1, op, f.length(), null);
                                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                                ObjectOutputStream oos = new ObjectOutputStream(baos);
                                oos.writeObject(p);
                                oos.flush();

                                b.clear();
                                b.put(baos.toByteArray());
                                b.flip();

                                server.send(b, tmpAddr);
                                oos.close();
                                baos.close();
                            }
                            k.interestOps(SelectionKey.OP_WRITE);
                        } else if (op == 3) {
                            op = -1;
                            //f = null;
                            lector.close();
                            fis.close();
                            k.interestOps(SelectionKey.OP_READ);
                        }
                        continue;
                    }
                }
            }

        } catch (Exception e) {
            e.printStackTrace();
        }

    }

    private static File[] getListaArchivos(String path) {
        File f = new File(path);
        return f.listFiles();
    }
}
