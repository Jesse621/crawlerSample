package com.ecust.companyInfo;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.openqa.selenium.By;
import org.openqa.selenium.Cookie;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;

import com.ecust.util.MQRedisClient;

public class LinkedinMgr {

	/** 国外 学校name-基本 */
	public static final String SCHOOL_E_BASIC = "SCHOOL_E_BASIC";

	public static final String SCHOOL_E_ERROR = "SCHOOL_E_ERROR";

	public static final String SCHOOL_E_URL = "SCHOOL_E_URL";

	public static final String SCHOOL_E_URL_PIC = "SCHOOL_E_URL_PIC";

	public static final String SCHOOL_E_URL_OSS = "SCHOOL_E_URL_OSS";

	public static final String SCHOOL_DIS_NAME = "SCHOOL_DIS_NAME";

	public static final String SCHOOL_E_LOGO = "SCHOOL_E_LOGO";

	
	MQRedisClient redisClient = new MQRedisClient("127.0.0.1", 6379);

	public void getLinkeUrl() throws Exception {
		for (int i = 852; i < 920; i++) {
			String ename = redisClient.lindexObject(String.class, SCHOOL_E_BASIC, i);
			if (StringUtils.isNoneBlank(ename)) {
				System.out.println(ename);
				String linkUrl = getbingUrl(ename);
				System.out.println(linkUrl);
				if (StringUtils.isBlank(linkUrl)) {
					redisClient.rpushObject("SCHOOL_E_ERROR", ename);
				} else {
					// 存储到map中
					Map<String, String> urlMap = new HashMap<>();
					urlMap.put("name", ename);
					urlMap.put("url", linkUrl);
					redisClient.rpushObject(SCHOOL_E_URL, urlMap);
				}

			}
			System.out.println(i);
		}
	}

	// 有验证码
	public String getgUrl(String name) throws Exception {
		ChromeOptions options = new ChromeOptions();
		WebDriver driver = new ChromeDriver();
		String url = "https://www.google.com.hk/search?q=" + name + " site%3Alinkedin.com";
		driver.get(url);
		driver.manage().deleteAllCookies();
		List<WebElement> elementList = driver
				.findElements(By.cssSelector("#rso > div > div > div:nth-child(1) > div > div > h3 > a"));
		String link = "";
		if (CollectionUtils.isNotEmpty(elementList)) {
			link = elementList.get(0).getAttribute("href");
		}
		if (StringUtils.isBlank(link)) {
			throw new Exception("未查到数据");
		}
		driver.quit();
		return link;
	}

	public String getbingUrl(String name) throws Exception {
		ChromeOptions options = new ChromeOptions();
		WebDriver driver = new ChromeDriver();
		String url = "https://www4.bing.com/search?q=" + name
				+ " site%3Alinkedin.com&go=Search&qs=ds&FORM=BESBTB&ensearch=1";
		driver.get(url);
		// driver.manage().deleteAllCookies();
		// WebElement submitElement =
		// driver.findElement(By.cssSelector("input#sb_form_go"));
		PageUtils.scrollToElementAndClick1("input#sb_form_go", driver);
		Thread.sleep(1000);
		driver.navigate().refresh();
		List<WebElement> elementList = driver
				.findElements(By.cssSelector("#b_results > li.b_algo:nth-child(1) > h2 > a"));
		if (CollectionUtils.isEmpty(elementList)) {
			elementList = driver.findElements(By.cssSelector("#b_results > li.b_algo:nth-child(2) > h2 > a"));
		}
		String link = "";
		if (CollectionUtils.isNotEmpty(elementList)) {
			link = elementList.get(0).getAttribute("href");
		}
		// if (StringUtils.isBlank(link)) {
		// throw new Exception("未查到数据");
		// }
		driver.quit();
		return link;
	}

	public String getPicUrl(String url) throws Exception {
		ChromeOptions options = new ChromeOptions();
		options.addArguments("user-data-dir=/Users/*/Library/Application Support/Google/Chrome/Default");
		WebDriver driver = new ChromeDriver(options);
		// String url = "http://www.linkedin.com/school/nanjing-university/";
		Cookie c5 = new Cookie("JSESSIONID", "\"ajax:9134082661032064420\"");
		// driver.manage().deleteAllCookies();
		driver.get(url);
		// driver.manage().addCookie(c5);
		Thread.sleep(1000);
		// driver.navigate().refresh();
		// Thread.sleep(3000);
		List<WebElement> elementList = driver
				.findElements(By.cssSelector("img.lazy-image.org-top-card-module__logo.loaded"));
		String picUrl = "";
		if (CollectionUtils.isNotEmpty(elementList)) {
			picUrl = elementList.get(0).getAttribute("src");
		}
		driver.quit();
		return picUrl;
	}

	public void getLinkePic() throws Exception {
		for (int i = 21; i < 40; i++) {
			Map<String, String> schoolMap = (Map<String, String>) redisClient.lindexObject(Map.class, SCHOOL_E_URL, i);
			if (!schoolMap.isEmpty()) {
				String name = schoolMap.get("name");
				String url = schoolMap.get("url");
				if (StringUtils.isBlank(url)) {
					continue;
				}
				// if (redisClient.sismember(SCHOOL_DIS_NAME, name)) {
				// continue;
				// }
				System.out.println("url-->" + url);
				String imgurl = getPicUrl(url);
				System.out.println("img-->" + imgurl);
				if (StringUtil.isNotBlank(imgurl)) {
					Map<String, String> picMap = new HashMap<>();
					picMap.put("name", name);
					picMap.put("url", imgurl);
					redisClient.rpushObject(SCHOOL_E_URL_PIC, picMap);
				}
				// redisClient.sadd(SCHOOL_DIS_NAME, name);
			}
			System.out.println(i);
		}
	}

	public static void main(String[] args) throws Exception {
		LinkedinMgr mgr = new LinkedinMgr();
		// mgr.getLinkeUrl();
		mgr.getLinkePic();
		// String url = "http://www.linkedin.com/school/nanjing-university/";
		// String imgUrl = mgr.getPicUrl(url);
		// System.out.println(imgUrl);
	}
}
