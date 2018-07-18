package com.liepin.crawler.grab;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.liepin.common.json.JsonUtil;
import com.liepin.crawler.common.AliOssUtil;
import com.liepin.crawler.common.AliOssUtil.FileDirType;
import com.liepin.crawler.common.CU;
import com.liepin.crawler.common.MQRedisClient;
import com.liepin.crawler.process.DataBaseService;

public class GrabSchoolMgr {
	protected static Logger logger = LoggerFactory.getLogger(GrabSchoolMgr.class);

	/** 学校name -url */
	public static final String SCHOOL_NAME_URL_LIST = "SCHOOL_NAME_URL_LIST";
	/** 学校name 详情页 图片 -url */
	public static final String SCHOOL_NAME_PIC_URL_LIST = "SCHOOL_NAME_PIC_URL_LIST";

	public static final String SCHOOL_NAME_URL_ERRORPAGE_LIST = "SCHOOL_NAME_URL_ERRORPAGE_LIST";

	public static final String SCHOOL_NAME_PIC_OSS_LIST = "SCHOOL_NAME_PIC_OSS_LIST";

	public static final String SCHOOL_NAME_OSS_ERROR_LIST = "SCHOOL_NAME_OSS_ERROR_LIST";

	

	public static List<String[]> getPostData(List<String[]> postDataList, String key, String value) {
		String[] entry = new String[] { key, value };
		postDataList.add(entry);
		return postDataList;
	}

	
	public void grabSchoolInfo() {
		// 每页30个 一共84页
		for (int i = 0; i < 85; i++) {
			try {
				processPage(i);
			} catch (Exception e) {
				logger.info("处理页数：" + i + "报错：" + e.getMessage());
				continue;
			}
		}
		logger.info("处理完毕");

	}

	private void processPage(Integer pageNo) {
		logger.info("当前开始处理页数：" + pageNo);
		System.out.println(pageNo);
		String url = "https://baike.baidu.com/wikitag/api/getlemmas";
		List<String[]> postDataList = new ArrayList<>();
		postDataList = getPostData(postDataList, "limit", "30");
		postDataList = getPostData(postDataList, "timeout", "3000");
		postDataList = getPostData(postDataList, "filterTags", "[0,0,0,0,0,0,0]");
		postDataList = getPostData(postDataList, "tagId", "60829");
		postDataList = getPostData(postDataList, "fromLemma", "false");
		postDataList = getPostData(postDataList, "contentLength", "40");
		postDataList = getPostData(postDataList, "page", pageNo.toString());
		String[] ret = GrabUtilWithDNS.grabSimple(url, JsonUtil.toJson(postDataList), null);
		if (!"200".equals(ret[0])) {
			redisClient.rpushObject(SCHOOL_NAME_URL_ERRORPAGE_LIST, pageNo);
			System.out.println("获取页面数据失败，页数：" + pageNo);
			return;
		}
		// 处理返回结果
		String schoolInfoJson = JsonUtil.getJsonData(ret[1], "lemmaList");
		List<Map<String, Object>> schoolList = (List<Map<String, Object>>) JsonUtil.toObject(schoolInfoJson,
				List.class);
		if (CollectionUtils.isEmpty(schoolList)) {
			System.out.println("未解析出数据，页数：" + pageNo);
			return;
		}
		for (Map<String, Object> map : schoolList) {
			Map<String, String> schoolMap = new HashMap<>();
			schoolMap.put("name", map.get("lemmaTitle").toString());
			schoolMap.put("url", map.get("lemmaUrl").toString());
			redisClient.rpushObject(SCHOOL_NAME_URL_LIST, schoolMap);
		}
		logger.info("处理页数：" + pageNo + "完毕");
	}

	public void grabSchoolDesc() {
		System.out.println("开始执行");
		for (int i = 0; i < 2411; i++) {
			Map<String, String> schoolMap = (Map<String, String>) redisClient.lindexObject(Map.class,
					SCHOOL_NAME_URL_LIST, i);
			if (!schoolMap.isEmpty()) {
				processSchoolDesc(schoolMap.get("name"), schoolMap.get("url"));
			}
			System.out.println();
		}
		System.out.println("执行完毕");
	}

	public void processSchoolDesc(String name, String url) {
		String ret = GrabUtilWithDNS3.getHtml(url);
		if (StringUtils.isBlank(ret)) {
			logger.info("获取详情页为空：" + url);
			System.out.println("获取详情页为空：" + url);
			return;
		}
		// 获取页面元素
		Document doc = Jsoup.parse(ret);
		Elements pic = doc.select(
				"body > div.body-wrapper.feature.feature_small.collegeSmall > div.feature_poster > div > div.poster-right > div > a > img");
		Elements store = doc.select(
				"body > div.body-wrapper.feature.feature_small.collegeSmall > div.feature_poster > div > div.poster-right > div > a");
		if (CollectionUtils.isEmpty(pic)) {
			pic = doc.select(
					"body > div.body-wrapper > div.content-wrapper > div > div.side-content > div.summary-pic > a > img");
			store = doc.select(
					"body > div.body-wrapper > div.content-wrapper > div > div.side-content > div.summary-pic > a");
		}
		String picUrl = "";
		if (CollectionUtils.isNotEmpty(pic)) {
			picUrl = pic.get(0).absUrl("src");
		}
		String storeUrl = "";
		if (CollectionUtils.isNotEmpty(store)) {
			Element element = store.first();
			storeUrl = "https://baike.baidu.com" + element.attr("href");
		}
		if (StringUtils.isBlank(picUrl) || StringUtils.isBlank(storeUrl)) {
			System.out.println("解析不完整：" + name);
			redisClient.rpushObject(SCHOOL_NAME_URL_ERRORPAGE_LIST, name);
		}
		Map<String, String> schoolMap = new HashMap<>();
		schoolMap.put("name", name);
		schoolMap.put("picUrl", picUrl);
		schoolMap.put("storeUrl", storeUrl);
		redisClient.rpushObject(SCHOOL_NAME_PIC_URL_LIST, schoolMap);
	}

	public void getErrorList() {
		for (int i = 0; i < 3; i++) {
			String name = (String) redisClient.lindexObject(String.class, SCHOOL_NAME_URL_ERRORPAGE_LIST, i);
			System.out.println(name);
		}
	}

	public InputStream getImageStream(String url) {
		try {
			HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection();
			connection.setReadTimeout(5000);
			connection.setConnectTimeout(5000);
			connection.setRequestMethod("GET");
			if (connection.getResponseCode() == HttpURLConnection.HTTP_OK) {
				InputStream inputStream = connection.getInputStream();
				byte[] data = readInputStream(inputStream);
				InputStream input = new ByteArrayInputStream(data);
				return input;
			}
		} catch (IOException e) {
			System.out.println("获取网络图片出现异常，图片路径为：" + url);
			e.printStackTrace();
		} catch (Exception e) {
			System.out.println("获取网络图片出现异常，图片路径为：" + url);
			e.printStackTrace();
		}
		return null;
	}

	public void getSchoolImg() throws Exception {
		for (int i = 1107; i < 2410; i++) {
			Map<String, String> schoolMap = (Map<String, String>) redisClient.lindexObject(Map.class,
					SCHOOL_NAME_PIC_URL_LIST, i);
			if (!schoolMap.isEmpty()) {
				String name = schoolMap.get("name");
				String url = schoolMap.get("picUrl");
				if (StringUtils.isBlank(url)) {
					continue;
				}
				InputStream in = getImageStream(url);
				String imgurl = AliOssUtil.upload(in, StringUtils.substringAfterLast(url, "/"), FileDirType.SCHOOL);
				Map<String, String> ossMap = new HashMap<>();
				ossMap.put("name", name);
				ossMap.put("url", imgurl);
				redisClient.rpushObject(SCHOOL_NAME_OSS_ERROR_LIST, ossMap);
			}
			System.out.println(i);
		}

	}

	public void getSchoolImgInFiles() throws Exception {
		for (int i = 0; i < 2410; i++) {
			Map<String, String> schoolMap = (Map<String, String>) redisClient.lindexObject(Map.class,
					SCHOOL_NAME_PIC_URL_LIST, i);
			if (!schoolMap.isEmpty()) {
				String name = schoolMap.get("name");
				String url = schoolMap.get("picUrl");
				if (StringUtils.isBlank(url)) {
					continue;
				}
				byte[] data = getImageStreamFile(url);
				// new一个文件对象用来保存图片，默认保存当前工程根目录
				String path = "/Users/jesse621/Liepin/schoolLogo/";
				File imageFile = new File(path + name + ".jpg");
				// 创建输出流
				FileOutputStream outStream = new FileOutputStream(imageFile);
				// 写入数据
				outStream.write(data);
				// 关闭输出流
				outStream.close();

			}
			System.out.println(i);
		}

	}

	public byte[] getImageStreamFile(String url) {
		try {
			HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection();
			connection.setReadTimeout(5000);
			connection.setConnectTimeout(5000);
			connection.setRequestMethod("GET");
			if (connection.getResponseCode() == HttpURLConnection.HTTP_OK) {
				InputStream inputStream = connection.getInputStream();
				byte[] data = readInputStream(inputStream);
				return data;
			}
		} catch (IOException e) {
			System.out.println("获取网络图片出现异常，图片路径为：" + url);
			e.printStackTrace();
		} catch (Exception e) {
			System.out.println("获取网络图片出现异常，图片路径为：" + url);
			e.printStackTrace();
		}
		return null;
	}

	public static byte[] readInputStream(InputStream inStream) throws Exception {
		ByteArrayOutputStream outStream = new ByteArrayOutputStream();
		// 创建一个Buffer字符串
		byte[] buffer = new byte[1024];
		// 每次读取的字符串长度，如果为-1，代表全部读取完毕
		int len = 0;
		// 使用一个输入流从buffer里把数据读取出来
		while ((len = inStream.read(buffer)) != -1) {
			// 用输出流往buffer里写入数据，中间参数代表从哪个位置开始读，len代表读取的长度
			outStream.write(buffer, 0, len);
		}
		// 关闭输入流
		inStream.close();
		// 把outStream里的数据写入内存
		return outStream.toByteArray();
	}

	public void insertSchoolLogo() {
		CU.loadConfig();
		for (int i = 0; i < 2410; i++) {
			Map<String, String> schoolMap = (Map<String, String>) redisClient.lindexObject(Map.class,
					SCHOOL_NAME_OSS_ERROR_LIST, i);
			if (!schoolMap.isEmpty()) {
				String name = schoolMap.get("name");
				String url = schoolMap.get("url");
				String sql = "INSERT INTO school_logo(name,logo_url) VALUE(?,?)";
				String[] valueList = { name, url };
				DataBaseService.insertToDBAndReturnId(3, sql, valueList);
			}
			System.out.println(i);
		}
	}

	public static void main(String[] args) throws Exception {
		GrabSchoolMgr schoolMgr = new GrabSchoolMgr();
		schoolMgr.getSchoolImgInFiles();
	}
}
