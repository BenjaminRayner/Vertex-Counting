import java.io.*;
import java.net.*;

class Server {
	public static void main(String args[]) throws Exception {
		if (args.length != 1) {
			System.out.println("usage: java Server port");
			System.exit(-1);
		}
		int port = Integer.parseInt(args[0]);

		ServerSocket ssock = new ServerSocket(port);
		System.out.println("listening on port " + port);

		//Every new connection will spawn new thread.
		while(true) {
			try {
				new Thread(new VertexCount(ssock.accept())).start();
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
	}
}

class VertexCount extends Thread
{
	Socket csock;
	VertexCount(Socket csock)
	{
		this.csock = csock;
	}

	public void run()
	{
		try {
			DataInputStream in = new DataInputStream(csock.getInputStream());
			DataOutputStream out = new DataOutputStream(csock.getOutputStream());
			while(true) {
				//Retrieve client data and convert to string.
				int len = in.readInt();
				byte[] payload = new byte[len];
				in.readFully(payload);

				//Edge Case
				if (len == 0) {
					out.writeInt(1);
					out.write('0');
					continue;
				}

				//Divide payload on vertex boundry
				int divider = len / 2;
				while ((payload[divider] != 32) && (payload[divider] != 10) && (divider != len - 1)) {
					++divider;
				}
				++divider;

				//Dispatch 2 worker threads for parsing vertex
				VertexParse firstHalf = new VertexParse(payload, 0, divider);
				VertexParse secondHalf = new VertexParse(payload, divider, len - divider);
				Thread thread1 = new Thread(firstHalf);
				Thread thread2 = new Thread(secondHalf);
				thread1.start();
				thread2.start();
				thread1.join();
				thread2.join();

				//Threads return. Add all vertex to hashtable for counting uniques.
				//Worst case size of hashtable is when every vertex is only one byte, divide by 2 to account for spaces/newlines.
				HashTable vertexSet = new HashTable(len / 2);
				for(int i = 0; i < firstHalf.vertCount; ++i) {
					vertexSet.add(firstHalf.vertexParsed[i]);
				}
				for(int i = 0; i < secondHalf.vertCount; ++i) {
					vertexSet.add(secondHalf.vertexParsed[i]);
				}
				//ASIDE: Could add vertices to a shared hashtable between threads but the synchronization seems to actually make it slower.

				//int -> UTF-8
				byte[] count = Integer.toString(vertexSet.items).getBytes("UTF-8");

				//Send number of vertex back to client.
				out.writeInt(count.length);
				out.write(count);
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
}

class VertexParse extends Thread
{
	byte[] payload;
	int[] vertexParsed;
	int vertCount = 0;
	int start;
	int dataLen;
	VertexParse(byte[] payload, int start, int dataLen) {
		this.payload = payload;
		this.start = start;
		this.dataLen = dataLen;
		this.vertexParsed = new int[dataLen / 2];
	}

	public void run() {
		//Parse vertices in byte array.
		int mark = start;
		for (int i = start; i < start + dataLen; ++i) {
			// New vertex on space or newline.
			if ((payload[i] == 32) || (payload[i] == 10)) {
				vertexParsed[vertCount] = byteArrayToInt(payload, mark, i);
				mark = i + 1;
				++vertCount;
			}
			//Does not end in newline
			else if (i == start + dataLen - 1) {
				vertexParsed[vertCount] = byteArrayToInt(payload, mark, i + 1);
				++vertCount;
			}
		}
	}
	
	int byteArrayToInt(byte[] data, int start, int end)
	{
		int result = 0;
		for (int i = start; i < end; i++) {
			int digit = (int)data[i] - (int)'0';
			result *= 10;
			result += digit;
		}
		return result;
	}
}

class HashTable
{
	int[] table;
	int size;
	int items = 0;
	HashTable(int size) {
		this.size = size;
		this.table = new int[size];
	}

	int hash(int key) {
		return key % this.size;
	}

	void add(int key) {
		int probe = hash(key);

		//While collisions...
		while(table[probe] != 0) {
			//Double check vertex is same
			if (table[probe] == key) return;
			probe = (probe + 1) % this.size; 
		}
		//Vertex not in table. Add
		table[probe] = key;
		++items;
	}
}