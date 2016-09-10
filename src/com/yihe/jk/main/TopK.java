package com.yihe.jk.main;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.CharBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Scanner;
import java.util.StringTokenizer;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import net.lingala.zip4j.core.ZipFile;

public class TopK {
	private static final String BASE_PATH = /*"f:"+File.separator+"jk"+*/File.separator;
	private static final String TEMP_PATH = BASE_PATH+"TopK_TEMP"+File.separator;

	public static void main(String[] args) {
		Scanner sc = new Scanner(System.in);
		System.out.print("please input password:");
		String psw = sc.next();
		sc.close();
		long startTime = System.currentTimeMillis();

		try {
			System.out.println("1.开始解压……");
			ZipFile zipFile = new ZipFile(BASE_PATH+"test.zip");
			String filename = BASE_PATH+"test.txt";
			
			if (zipFile.isEncrypted()) {
				zipFile.setPassword(psw);
			}
			zipFile.extractAll(BASE_PATH);
			System.out.println("解压完成!");
			System.out.println("2.开始分析……");
			
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


	/**
	 * 将大文件拆分成较小的文件，进行预处理
	 * 
	 * @throws IOException
	 */
	private void preProcess() throws IOException {
		File file = new File(TEMP_PATH);
		deleteDir(file);
		if (!file.exists())
			file.mkdirs();
		File dataFile = new File(filename);

		int size = 0;

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

			threadPool.execute(preThread);
	       
		}
		threadPool.shutdown();
		//线程池执行完才执行下一步
		while (true) {  
            if (threadPool.isTerminated()) {  
                break;
            }  
        }  

		reader.close();
		fis.close();

	}

	private boolean process() throws IOException {
		Path target = Paths.get(TEMP_PATH);

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
			System.out.println(i + ":" + word/*+"="+freq*/);
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
		private String  data;
		private String threadNo;

		Map<String, String> wordFreqMap = new HashMap<String, String>();
		// filewriter缓存
		Map<String, FileWriter> fwMap = new HashMap<String, FileWriter>();

		public PreThread(String threadNo,String data) {
			this.data = data;
			this.threadNo = threadNo;
		}

		@Override
		public void run() {
			System.out.println("线程["+threadNo+"]启动……");

			try {

				 StringTokenizer token=new StringTokenizer(data/*new String(rBuffer.array(),"utf-8")*/,DELIMITER);   
				 data = null;
				while ( token.hasMoreElements() ){
					String word = token.nextToken();
					if (wordFreqMap.containsKey(word)) {
						wordFreqMap.put(word, wordFreqMap.get(word) + REPLACE_CHAR);
					} else {
						wordFreqMap.put(word, REPLACE_CHAR);
					}
				}

				for (String word : wordFreqMap.keySet()) {
					
					String filePath = TEMP_PATH + FILE_PRE + word + SUFFIX_WORD;
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
