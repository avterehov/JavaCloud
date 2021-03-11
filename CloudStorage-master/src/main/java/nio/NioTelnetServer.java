package nio;

import java.io.File;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.HashMap;
import java.util.Map;

import static java.nio.file.StandardCopyOption.REPLACE_EXISTING;

public class NioTelnetServer {
	private final ByteBuffer buffer = ByteBuffer.allocate(512);

	public static final String LS_COMMAND = "\tls          view all files from current directory\n\r";
	public static final String MKDIR_COMMAND = "\tmkdir       create new directory\n\r";
	public static final String CHANGE_NICKNAME_COMMAND = "\tnick        change nickname\n\r";
	public static final String TOUCH_COMMAND = "\ttouch (file name)        create file in a current directory\n\r";
	public static final String RM_COMMAND = "\trm  (file name)     delete file in a current directory\n";
	public static final String COPY_COMMAND = "\tcopy  (source, target)     copy file from sourse directory to target directory\n";
	public static final String CAT_COMMAND = "\tcat (file name)     show data in file\n";


	private Map<String, SocketAddress> clients = new HashMap<>();
	private Path currentPath = Paths.get("server" );



	public NioTelnetServer() throws IOException {
		ServerSocketChannel server = ServerSocketChannel.open(); // открыли
		server.bind(new InetSocketAddress(1234));
		server.configureBlocking(false); // ВАЖНО
		Selector selector = Selector.open();
		server.register(selector, SelectionKey.OP_ACCEPT);
		System.out.println("Server started");
		while (server.isOpen()) {
			selector.select();
			var selectionKeys = selector.selectedKeys();
			var iterator = selectionKeys.iterator();
			while (iterator.hasNext()) {
				var key = iterator.next();
				if (key.isAcceptable()) {
					handleAccept(key, selector);
				} else if (key.isReadable()) {
					handleRead(key, selector);
				}
				iterator.remove();
			}
		}
	}

	private void handleRead(SelectionKey key, Selector selector) throws IOException {
		SocketChannel channel = (SocketChannel) key.channel();
		SocketAddress client = channel.getRemoteAddress();
		String nickname = "";
		int readBytes = channel.read(buffer);
		if (readBytes < 0) {
			channel.close();
			return;
		} else if (readBytes == 0) {
			return;
		}

		buffer.flip();
		StringBuilder sb = new StringBuilder();
		while (buffer.hasRemaining()) {
			sb.append((char) buffer.get());
		}
		buffer.clear();


		// TODO: 05.03.2021
		// touch (имя файла) - создание файла - done
		// mkdir (имя директории) - создание директории done
		// cd (path) - перемещение по дереву папок  not done
		// rm (имя файла или папки) - удаление объекта done
		// copy (src, target) - копирование файла
		// cat (имя файла) - вывод в консоль содержимого

		if (key.isValid()) {
			String command = sb.toString()
					.replace("\n", "")
					.replace("\r", "");
			if ("--help".equals(command)) {
				sendMessage(LS_COMMAND, selector, client);
				sendMessage(MKDIR_COMMAND, selector, client);
				sendMessage(TOUCH_COMMAND, selector, client);
				sendMessage(RM_COMMAND, selector, client);
				sendMessage(COPY_COMMAND, selector, client);
				sendMessage(CAT_COMMAND, selector, client);
				sendMessage(CHANGE_NICKNAME_COMMAND + "\n\r", selector, client);
			} else if (command.startsWith("nick ")) {
				nickname = command.split(" ")[1];
				clients.put(nickname, client);
				System.out.println("Client [" + client.toString() + "] changes nickname on [" + nickname + "]");
			} else if ("ls".equals(command)) {
				sendMessage(getFilesList().concat("\n\r"), selector, client);

			}else if (command.startsWith("touch ")){
				String fileName = command.split(" ")[1];
				Path path = Paths.get(currentPath.toString(), fileName );
				if (!Files.exists(path)){
					Files.createFile(path);
					System.out.println("File " + fileName + " created");
					System.out.println(Files.exists(path));
					System.out.println(path.toAbsolutePath().toString());
				}

// mkdir (имя директории) - создание директории

            }else if (command.startsWith("mkdir ")){
                String newDir = command.split(" ")[1];
                try{
					if (!Files.exists(Path.of(currentPath.toString(), newDir))) {
						Files.createDirectory(Path.of(currentPath.toString(), newDir));
						System.out.println("new directory created");
					}else {
						System.out.println("directory exists already");
					}
				} catch(FileAlreadyExistsException e){

				} catch (IOException e) {
					e.printStackTrace();
				}

// rm (имя файла или папки) - удаление объекта
			} else if (command.startsWith("rm ")){
				String fileToDelete = command.split(" ")[1];
				Path path = Paths.get(currentPath.toString(), fileToDelete);
				if (Files.exists(path)) {
					try {
						Files.delete(path);
					} catch (IOException e) {
						e.printStackTrace();
					}
				}else {
					sendMessage("File do not exists",selector, client);
				}


// cd (path) - перемещение по дереву папок
//работает только в одну сторону. Движение наверх пока не соображу как реализовать.

			}else if (command.startsWith("cd ")) {
				String cd = command.split(" ")[1];
				Path path = Paths.get(currentPath.toString(), cd);
				if (Files.exists(path)) {
					currentPath = Paths.get(path.toAbsolutePath().toString());
					sendMessage(("new directory: \n\r" + currentPath.toAbsolutePath().toString()) + "\n\r", selector, client);
				} else {
					System.out.println("DIR/FILE DOES NOT EXISTS\n\r ");
					sendMessage("DIR/FILE DOES NOT EXISTS\n\r ", selector, client);
				}

// copy (src, target) - копирование файла

			}else if (command.startsWith("copy ")){
				String dir = command.split(" ")[1];
				String dir2 = command.split(" ")[2];
				Path srcPath = Paths.get(dir);
				Path targetPath = Paths.get(dir2);
				if (Files.exists(srcPath) && Files.exists(targetPath)) {
					Files.copy(srcPath, targetPath, REPLACE_EXISTING);
				}else {
					System.out.println("directory/s are wrong or does not exist");
					sendMessage("directory/s are wrong or does not exist\n\r", selector, client);
				}

// cat (имя файла) - вывод в консоль содержимого
			}else if (command.startsWith("cat ")){
					String fileName = command.split(" ")[1];
					Path path = Paths.get(currentPath.toString(), fileName);
					byte[] bytes = Files.readAllBytes(path);
					for (byte b : bytes) {
						System.out.print((char) b);
					}
					String data = new String(bytes);
					sendMessage(data +"\n\r",selector, client);





			} else if ("exit".equals(command)) {
				System.out.println("Client logged out. IP: " + channel.getRemoteAddress());
				channel.close();
				return;
			}
		}

		for (Map.Entry<String, SocketAddress> clientInfo : clients.entrySet()) {
			if (clientInfo.getValue().equals(client)) {
				nickname = clientInfo.getKey();
			}
		}
		sendName(channel, nickname);
	}

	private void sendName(SocketChannel channel, String nickname) throws IOException {
		if (nickname.isEmpty()) {
			nickname = channel.getRemoteAddress().toString();
		}
		channel.write(
				ByteBuffer.wrap(nickname
						.concat(">: ")
						.getBytes(StandardCharsets.UTF_8)
				)
		);
	}

	private String getFilesList() {
		return String.join("\t", new File("server").list());
	}

	private void sendMessage(String message, Selector selector, SocketAddress client) throws IOException {
		for (SelectionKey key : selector.keys()) {
			if (key.isValid() && key.channel() instanceof SocketChannel) {
				if (((SocketChannel) key.channel()).getRemoteAddress().equals(client)) {
					((SocketChannel) key.channel())
							.write(ByteBuffer.wrap(message.getBytes(StandardCharsets.UTF_8)));
				}
			}
		}
	}

	private void handleAccept(SelectionKey key, Selector selector) throws IOException {
		SocketChannel channel = ((ServerSocketChannel) key.channel()).accept();
		channel.configureBlocking(false);
		System.out.println("Client accepted. IP: " + channel.getRemoteAddress());
		channel.register(selector, SelectionKey.OP_READ, "some attach");
		channel.write(ByteBuffer.wrap("Hello user!\n".getBytes(StandardCharsets.UTF_8)));
		channel.write(ByteBuffer.wrap("Enter --help for support info\n".getBytes(StandardCharsets.UTF_8)));
		sendName(channel, "");
	}

	public static void main(String[] args) throws IOException {
		new NioTelnetServer();

	}
}
