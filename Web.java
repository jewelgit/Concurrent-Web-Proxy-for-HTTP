
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketAddress;
import java.net.InetSocketAddress;
import java.net.InetAddress;
import java.io.IOException;
import java.io.FileInputStream;
public class Web {
	private static final int MAXREQUEST = 65535, MAXRESPONSE = 200 * 1024 * 1024, TEMPBUFF = 1024;
	private static int clientcount = 0;
	private static Socket socket;
	private static final byte[][] blocked = new byte[100][255];
	public static void main(String[] args) throws Exception {
		if (args.length < 1) {
			System.out.println("Usage: java Web [port] [blacklist]");
			System.exit(1);
		}
		if (args.length == 2) {
			try {
				FileInputStream fin = new FileInputStream(args[1]);
				int i = 0, o = 0, j = 0;
				while ((i = fin.read()) != -1) {
					if (i == System.getProperty("line.separator").charAt(0)) {
						o++;
						j = 0;
					} else if (i != '\n')
						blocked[o][j++] = (byte) i;
				}
				fin.close();
			} catch (IOException e) {
				System.out.println("No file");
				System.exit(1);
			}
		}
		ServerSocket listener = new ServerSocket();
		InetSocketAddress sockaddr = new InetSocketAddress( Integer.parseInt(args[0]));
		System.out.println("Proxy Server Listening on socket " + args[0]);
		listener.bind(sockaddr);
		try {
			while (true) {
				socket = listener.accept();
				clientcount++;
				byte[] tmp = new byte[TEMPBUFF],request = new byte[MAXREQUEST];
				int index = 0, start = 0, end = TEMPBUFF, totalrequest = 0;
				boolean maxrequest = false;
				try {
					while ((index = socket.getInputStream().read(tmp, 0, TEMPBUFF)) != -1) {
						totalrequest += index;
						if (totalrequest > MAXREQUEST) {
							System.out.println("Maximum Request Bytes: " + totalrequest);
							maxrequest = true;
							break;
						}
						int tmpindex = 0;
						for (int i = start; i < end; i++)
							request[i] = tmp[tmpindex++];
						if (index < TEMPBUFF) {
							break;
						} else if (index == TEMPBUFF) {
							start = end;
							end = end + TEMPBUFF;
						}
					}
					int ret = 9;
					if (!maxrequest && byteTrim(request).length > 0)
						ret = doHTTP(socket, byteTrim(request));
					if (ret != 1) 
						errorHandler(new Exception(""), socket, ret);
				}catch(IOException e) {
					errorHandler(e, socket, -9);
				}finally {
					if(socket !=null)socket.close();
				} 
			}
		} catch (IOException e) {
			errorHandler(e, socket, -9);
		} finally {
			if(listener!=null)listener.close();
		}
	}
	// trim byte array
	public static byte[] byteTrim(byte[] xxx) {
		int xxxSize = 0;
		for (int i = 0; i < xxx.length; i++)
			if (xxx[i] != 0)
				xxxSize++;
		byte[] ret = new byte[xxxSize];
		int retIndex = 0;
		for (int x = 0; x < xxxSize; x++) {
			if (xxx[x] != 0)
				ret[retIndex] = xxx[x];
			retIndex++;
		}
		return ret;
	}
	// function to write Exception for NO IP found
	public static void errorHandler(Exception ex, Socket socket, int error) {
		try {
			if (error == -1) {
				System.out.println(" Max Byte Response.");
				socket.getOutputStream().write("Max Byte Response. ".getBytes());
			} else if (error == -2) {
				System.out.println("Bad Port. ");
				socket.getOutputStream().write("Bad Port.".getBytes());
			} else if (error == -3) {
				System.out.println("No IP Address Found.");
				socket.getOutputStream().write("No IP Address Found. ".getBytes());
			} else if (error == -4) {
				System.out.println("Connection Error.");
				socket.getOutputStream().write("Connection Error.".getBytes());
			} else if (error == -5) {
				System.out.println("Blocked Site.");
				socket.getOutputStream().write("Blocked Site.".getBytes());
			} else
				System.out.println("Connecion Reset");
		} catch (IOException e) {
			errorHandler(e, socket, -9);
		}
	}
	// return preferred IP by checking connection
	public static InetAddress dns(InetAddress[] inetAddr) {
		int ret = 0;
		long start = 0L, end = 0L, letmesleep = 10000L;
		Socket sock = null;
		SocketAddress sockaddr = null;
		for (int i = 0; i < inetAddr.length; i++) {
			start = System.nanoTime();
			try {
				sockaddr = new InetSocketAddress(inetAddr[i], 80);
				sock = new Socket();
				sock.connect(sockaddr, 1000);
				end = System.nanoTime();
				if (letmesleep > end - start) ret = i;
				letmesleep = end - start;
				sock.close();
			} catch (IOException e) {
				errorHandler(e, socket, -4);
			}
		}
		return inetAddr[ret];
	}
	// collect the page by requesting to client's request URL -1: response over 200 Mb, -2: connection error
	public static int requestHTTP(Socket clientSocket, InetAddress inetAddr, byte[][] requestUrl, byte[] request) {
		Socket destSocket = null;
		byte[] tmp = new byte[TEMPBUFF];
		int buffindex = 0, ret = 1;
		try {
			destSocket = new Socket();
			destSocket.connect(new InetSocketAddress(inetAddr, Integer.parseInt(new String(requestUrl[1]).trim())));
			destSocket.getOutputStream().write(request);
			destSocket.getOutputStream().flush();
			while ((buffindex = destSocket.getInputStream().read(tmp, 0, TEMPBUFF)) != -1) {
				byte[] length = new byte[10];
				for(int i =0; i <TEMPBUFF - "Content-Length:".getBytes().length ; i++) {
					if(tmp[i]=='C' && tmp[i+1]=='o' && tmp[i+2]=='n' &&tmp[i+3]=='t' && tmp[i+4]=='e' && tmp[i+5]=='n' && tmp[i+6]=='t' && tmp[i+7]=='-' && tmp[i+8]=='L' && tmp[i+9]=='e' && tmp[i+10]=='n' && tmp[i+11]=='g' && tmp[i+12]=='t' && tmp[i+13]=='h' && tmp[i+14]==':') {
						int index =0;
						for (int j =i+15; j< i+25; j++) {
							length[index++]=tmp[j];
							if(tmp[j+1]=='\r' || tmp[j+1]==32  )
								break;
						}
					}
				}
				int totalresponse = 0;
				try{ totalresponse = Integer.parseInt(new String(length).trim());
				}catch(NumberFormatException e) {}
				if (totalresponse  > MAXRESPONSE) {
					ret= -1;break;
				}
				if (buffindex == TEMPBUFF) {
					clientSocket.getOutputStream().write(tmp, 0, buffindex);
					clientSocket.getOutputStream().flush();
				} else {
					clientSocket.getOutputStream().write(byteTrim(tmp), 0, byteTrim(tmp).length);
					clientSocket.getOutputStream().flush();
					break;
				}
				for (int i = 0; i < tmp.length; i++)
					tmp[i] = 0;
			}
		} catch (IOException e) {
			// connection error bad port
			ret = -2;
			errorHandler(e, socket, -2);
		} finally {
			try {
				destSocket.getOutputStream().flush();
				destSocket.getOutputStream().close();
				destSocket.close();
			} catch (IOException e) {
				errorHandler(e, socket, -9);
			}
		}
		return ret;
	}
	// byte to char
	public static char[] b2c(byte[] in) {
		char[] out = new char[in.length];
		for (int x = 0; x < in.length; x++) {
			out[x] = (char) in[x];
		}
		return out;
	}
	// compare byte[]
	public static boolean isEqual(byte[] x, byte[] y) {
		boolean ret = true;
		if (x.length != y.length) return false;
		for (int i = 0; i < y.length; i++) {
			if (x[i] != y[i]) {
				ret = false;
				break;
			}
		}
		return ret;
	}
	// parse http://url.com:8080 into byte[][] array
	public static byte[][] doParse(byte[] in) {
		int start = 0, end = 0;
		byte[][] tmp = new byte[4][300];
		// pass CONNECT, GET etc
		for (int i = 0; i < in.length; i++) {
			if (in[i] == 32) {
				start = i + 1;
				break;
			}
		}
		// find http:// https:// as start, find a space as end , tmp[3] = http:// or https://
		for (int i = start; i < in.length; i++) {
			if (in[i] == 'h' && in[i + 1] == 't' && in[i + 2] == 't' && in[i + 3] == 'p' && in[i + 4] == ':' && in[i + 5] == '/' && in[i + 6] == '/') {
				start = i + 7;
				tmp[3] = "http://".getBytes();
			}
			if (in[i] == 'h' && in[i + 1] == 't' && in[i + 2] == 't' && in[i + 3] == 'p' && in[i + 4] == 's' && in[i + 5] == ':' && in[i + 6] == '/' && in[i + 7] == '/') {
				start = i + 8;
				tmp[3] = "https://".getBytes();
			}
			if (in[i] == 32) {
				end = i + 1;
				break;
			}
		}
		// tmp[0] hostname www.hostname.com
		int index = 0;
		boolean findport = false;
		for (int i = start; i < end; i++) {
			tmp[0][index++] = in[i];
			if (in[i + 1] == ':') {
				findport = true;
				start = i + 2;
				break;
			}
			if (in[i + 1] == '/') {
				start = i + 1;
				break;
			}
			if (in[i + 1] == 32)
				break;
		}
		index = 0;
		// tmp[1] : Port
		if (findport) {
			for (int i = start; i < end; i++) {
				tmp[1][index++] = in[i];
				if (in[i + 1] == '/') {
					start = i + 1;
					break;
				}
				if (in[i + 1] == 32) {
					start = i + 2;
					break;
				}
			}
		} else 
			tmp[1] = "80".getBytes();
		// tmp[2] path
		index = 0;
		for (int i = start; i < end; i++) {
			tmp[2][index++] = in[i];
			if (in[i] == 32) {
				end = i;
				break;
			}
		}
		return tmp;
	}
	// HTTP response -1: Max Response: 200Mb, -2: bad port, -3: NO IP, -4: connection error, -5: blocked site, 0: response length 0, -9: etc
	public static int doHTTP(Socket clientSocket, byte[] request) {
		try {
			byte[][] inputUrl = doParse(request);
			int port = 80;
			System.out.print("(" + clientcount + ") REQ: ");
			if (inputUrl[0][0] != 0) {
				System.out.print(b2c(byteTrim(inputUrl[3])));
				System.out.print(b2c(byteTrim(inputUrl[0])));
				System.out.print(":");
				Integer.parseInt(new String(inputUrl[1]).trim());
				System.out.println(port);
			} else return -9;
			for (int i = 0; i < blocked.length; i++)
				if (isEqual(byteTrim(inputUrl[0]), byteTrim(blocked[i]))) return -5;
			if (inputUrl[0] == null) return -9;
			if (inputUrl[0][0] == 0 && inputUrl[0][1] == 0) return -9;
			InetAddress[] inetAddress = InetAddress.getAllByName(new String(inputUrl[0], 0, inputUrl[0].length).trim());
			InetAddress preferedInetAddress = null;
			if (inetAddress != null)
				preferedInetAddress = dns(inetAddress);
			// deliver reqested page
			int ret = requestHTTP(clientSocket, preferedInetAddress, inputUrl, request);
			if (ret != 1) return ret;
		} catch (IOException e) {
			errorHandler(e, socket, -2);
		}
		return 1;
	}
}
