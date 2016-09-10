package com.yihe.jk.main;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.FileAttribute;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Scanner;
import java.util.Set;
import java.util.StringTokenizer;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import net.lingala.zip4j.core.ZipFile;

public class TopK {
	private static final String root = "f://jk//TopK_TEMP//";

	public static void main(String[] args) {
		Scanner sc = new Scanner(System.in);
		System.out.print("please input password:");
		String psw = sc.next();
		long startTime = System.currentTimeMillis();

		try {
			System.out.println("1.开始解压……");
			ZipFile zipFile = new ZipFile("f://jk//test.zip");

			if (zipFile.isEncrypted()) {
				zipFile.setPassword(psw);
			}
			zipFile.extractAll("f://jk");
			System.out.println("解压完成!");
			System.out.println("2.开始分析……");
			String filename = "f://jk//test.txt";
			TopK topk = new TopK(filename);
			topk.preProcess();
			System.out.println(" 2.1 分解文件完成!");
			topk.process();
			System.out.println(" 2.2 分析完成!");
		} catch (Exception e) {
			e.printStackTrace();
		} finally {
			System.out.println("执行耗时 : "
					+ (System.currentTimeMillis() - startTime) / 1000f + " 秒 ");
		}
	}
	private static final int THREAD_SIZE = 100;
	private static final String SUFFIX_WORD = ".txt";
	private static final String DELIMITER = ";";
	private static final String FILE_PRE = "word_";

	private static final String REPLACE_CHAR = "?";

	// 单位为M
	private static final int FILE_CACHE = 1* 1024 * 1024;

	private String filename = "";
	// 前几个频率最高
	private static final long MAX_TOPK = 5;

	public TopK(final String filename) {
		this.filename = filename;
	}

//	private final static int bufSize = FILE_CACHE * 1024 * 1024;

	/**
	 * 将大文件拆分成较小的文件，进行预处理
	 * 
	 * @throws IOException
	 */
	private void preProcess() throws IOException {
		File file = new File(root);
		deleteDir(file);
		if (!file.exists())
			file.mkdirs();
		File dataFile = new File(filename);
//		FileChannel fcin = new RandomAccessFile(dataFile, "r").getChannel();
		// RandomAccessFile raFile = new RandomAccessFile(dataFile, "r");
		int size = 0;
		// MappedByteBuffer out = fcin.map(FileChannel.MapMode.READ_ONLY, 0,
		// bufSize);
//		String line= "";

//		ByteBuffer rBuffer = ByteBuffer.allocate(bufSize);
		BufferedInputStream fis = new BufferedInputStream(new FileInputStream(
				dataFile));
		// 用5M的缓冲读取文本文件
		BufferedReader reader = new BufferedReader(new InputStreamReader(fis,
				"utf-8"));
		CharBuffer rBuffer = CharBuffer.allocate(FILE_CACHE); 
		ExecutorService threadPool = Executors.newFixedThreadPool(THREAD_SIZE); 
		int i = 0;
		while ((size = reader.read(rBuffer.array())) != -1) {
			++i;
			
			PreThread preThread = new PreThread(i+"",rBuffer.toString());
//			rBuffer.clear();
//			Thread t = new Thread(preThread); 
//			 t.start();
			threadPool.execute(preThread);
	       
		}
		threadPool.shutdown();
		while (true) {  
            if (threadPool.isTerminated()) {  
                break;
            }  
        }  

//		rBuffer.clear();
		reader.close();
		fis.close();
//		fcin.close();

	}

	private boolean process() throws IOException {
		Path target = Paths.get(root);

		HashMap<Long, String> wordFreqMap = new HashMap<Long, String>();
		List<Long> wordFreqList = new ArrayList<Long>();
		handleFile(target, wordFreqMap, wordFreqList);
		// 倒序
		Collections.sort(wordFreqList, new LongDescComparator());
		int i = 0;
		for (Long freq : wordFreqList) {
			++i;
			String word = wordFreqMap.get(freq);
			if (word!=null){
				word = word.replace(SUFFIX_WORD, "");
				word = word.replace(FILE_PRE, "");
			}
			System.out.println(i + ":" + word+"="+freq);
		}

		return true;
	}

	/**
	 * 执行取所有文件名称及大小
	 * 
	 * @param parent
	 * @return
	 * @throws IOException
	 */
	private void handleFile(Path parent, HashMap<Long, String> wordFreqMap,
			List<Long> wordFreqList) throws IOException {
		// Path target = Paths.get(dir);
		if (Files.exists(parent) || Files.isDirectory(parent)) {
			File tempFile = parent.toFile();
			File[] files = tempFile.listFiles();
			for (File file : files) {
				String fileName = file.getName();
				long fileLen = file.length();
				if (wordFreqMap.size() < MAX_TOPK) {
					wordFreqMap.put(fileLen, fileName);
					wordFreqList.add(fileLen);
				} else {
					long min = Collections.min(wordFreqList);
					if (fileLen > min) {
						wordFreqMap.remove(min);
						wordFreqList.remove(min);
						wordFreqMap.put(fileLen, fileName);
						wordFreqList.add(fileLen);
					}

				}
			}
		}

	}

	// 自定义比较器：long倒序
	static class LongDescComparator implements Comparator {
		public int compare(Object object1, Object object2) {// 实现接口中的方法
			Long p1 = (Long) object1; // 强制转换
			Long p2 = (Long) object2;
			if (p1 - p2 > 0)
				return -1;
			else if (p1 - p2 == 0)
				return 0;
			else
				return 1;
		}
	}

	/**
	 * 创建文件
	 * 
	 * @throws IOException
	 */
	public static void createFile(String splitFilename) throws IOException {
		Path target = Paths.get(splitFilename);
		Set<PosixFilePermission> perms = PosixFilePermissions
				.fromString("rw-rw-rw-");
		FileAttribute<Set<PosixFilePermission>> attr = PosixFilePermissions
				.asFileAttribute(perms);
		Files.createFile(target, attr);
	}

	/**
	 * 文件内容追加
	 * 
	 * @param splitFilename
	 * @param bytes
	 * @return
	 * @throws IOException
	 */
	public static Path appendToFile(String splitFilename, byte[] bytes)
			throws IOException {
		if (bytes != null) {
			Path target = Paths.get(splitFilename);
			if (target == null) {
				createFile(splitFilename);
			}
			return Files.write(target, bytes);// , options)
		}

		return null;
	}

	/**
	 * 递归删除目录下的所有文件及子目录下所有文件
	 * 
	 * @param dir
	 *            将要删除的文件目录
	 */
	private static boolean deleteDir(File dir) {
		if (dir == null)
			return true;
		if (dir.isDirectory()) {
			String[] children = dir.list();
			// 递归删除目录中的子目录下
			for (int i = 0; i < children.length; i++) {
				boolean success = deleteDir(new File(dir, children[i]));
				if (!success) {
					return false;
				}
			}
		}
		// 目录此时为空，可以删除
		return dir.delete();
	}

	class PreThread implements Runnable {
//		private ByteBuffer rBuffer;
		private String  data;
		private String threadNo;
//		Map<String, String> wordFreqMap;
//		Map<String, FileWriter> fwMap;
		Map<String, String> wordFreqMap = new HashMap<String, String>();
		// filewriter缓存
		Map<String, FileWriter> fwMap = new HashMap<String, FileWriter>();

		public PreThread(String threadNo,String data/*ByteBuffer rBuffer*//*, Map<String, String> wordFreqMap,
				Map<String, FileWriter> fwMap*/) {
//			this.rBuffer = rBuffer;
			this.data = data;
			this.threadNo = threadNo;
//			this.wordFreqMap = wordFreqMap;
//			this.fwMap = fwMap;
		}

		@Override
		public void run() {
			System.out.println("线程["+threadNo+"]启动……");
//			System.out.println(data);
			
//			System.out.println("length:"+data.length()+";hashcode:"+data.hashCode());
			try {
				/*FileWriter ftemp = new FileWriter("f://jk//temp//"+System.currentTimeMillis()+".txt");
				ftemp.write(data);
				ftemp.close();*/
				 StringTokenizer token=new StringTokenizer(data/*new String(rBuffer.array(),"utf-8")*/,DELIMITER);   
//				String split[] = new String(rBuffer.array(),"utf-8").split(delimiter);
				 data = null;
				while ( token.hasMoreElements() ){
					String word = token.nextToken();
					if (wordFreqMap.containsKey(word)) {
						wordFreqMap.put(word, wordFreqMap.get(word) + REPLACE_CHAR);
					} else {
						wordFreqMap.put(word, REPLACE_CHAR);
					}
				}
				/*for (String word : split) {
					if (wordFreqMap.containsKey(word)) {
						wordFreqMap.put(word, wordFreqMap.get(word) + REPLACE_CHAR);
					} else {
						wordFreqMap.put(word, REPLACE_CHAR);
					}
				}*/
//				split = null;
				for (String word : wordFreqMap.keySet()) {
					
					String filePath = root + FILE_PRE + word + SUFFIX_WORD;
//					appendToFile(filePath,wordFreqMap.get(word).getBytes());
					File file = new File(filePath);
					if (!file.exists())
						file.createNewFile();

					FileWriter fw = null;
					if (fwMap.containsKey(filePath)) {
						fw = fwMap.get(filePath);
					}
					if (fw == null) {
						fw = new FileWriter(file,true);
						fwMap.put(filePath, fw);
					}
					/*for (char c:wordFreqMap.get(word).toCharArray()){
						fw.append(c);
					}*/
					
					fw.append(wordFreqMap.get(word));
					
				}
				wordFreqMap.clear();
				Collection<FileWriter> c = fwMap.values();
				for (FileWriter fw : c) {
					if (fw != null){
						
						fw.flush();
						fw.close();
					}
						
				}
				fwMap.clear();
				wordFreqMap = null;
				fwMap = null;
				System.out.println("线程["+threadNo+"]结束");
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
	}

}
