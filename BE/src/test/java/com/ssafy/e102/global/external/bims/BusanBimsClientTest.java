package com.ssafy.e102.global.external.bims;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.StringReader;
import java.time.Duration;

import javax.xml.parsers.DocumentBuilderFactory;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.test.util.ReflectionTestUtils;
import org.w3c.dom.Element;
import org.xml.sax.InputSource;

class BusanBimsClientTest {

	@Test
	@DisplayName("BIMS serviceKey의 +와 =를 query-safe encoding으로 변환한다")
	void encodedServiceKeyEscapesPlusAndEquals() {
		BusanBimsClient client = new BusanBimsClient(
			new RestTemplateBuilder(),
			new BusanBimsProperties("https://apis.data.go.kr/6260000/BusanBIMS", "abc+def==",
				Duration.ofSeconds(5), Duration.ofSeconds(5)));

		String encodedServiceKey = ReflectionTestUtils.invokeMethod(client, "encodedServiceKey");

		assertThat(encodedServiceKey).isEqualTo("abc%2Bdef%3D%3D");
	}

	@Test
	@DisplayName("이미 인코딩된 BIMS serviceKey는 중복 인코딩하지 않는다")
	void encodedServiceKeyKeepsAlreadyEncodedValue() {
		BusanBimsClient client = new BusanBimsClient(
			new RestTemplateBuilder(),
			new BusanBimsProperties("https://apis.data.go.kr/6260000/BusanBIMS", "abc%2Bdef%3D%3D",
				Duration.ofSeconds(5), Duration.ofSeconds(5)));

		String encodedServiceKey = ReflectionTestUtils.invokeMethod(client, "encodedServiceKey");

		assertThat(encodedServiceKey).isEqualTo("abc%2Bdef%3D%3D");
	}

	@Test
	@DisplayName("BIMS 도착 슬롯은 저상버스 차량번호와 남은 정류장 수를 함께 파싱한다")
	void arrivalSlotParsesLowFloorVehicleAndRemainingStopCount() throws Exception {
		BusanBimsClient client = new BusanBimsClient(
			new RestTemplateBuilder(),
			new BusanBimsProperties("https://apis.data.go.kr/6260000/BusanBIMS", "key",
				Duration.ofSeconds(5), Duration.ofSeconds(5)));

		Object slot = ReflectionTestUtils.invokeMethod(client, "arrivalSlot", item("""
			<item>
			  <min1>14</min1>
			  <lowplate1>1</lowplate1>
			  <carno1>1618</carno1>
			  <station1>2</station1>
			  <min2>7</min2>
			  <lowplate2>0</lowplate2>
			  <carno2>1234</carno2>
			  <station2>1</station2>
			</item>
			"""));

		assertThat((Integer)ReflectionTestUtils.invokeMethod(slot, "remainingMinute")).isEqualTo(14);
		assertThat((Boolean)ReflectionTestUtils.invokeMethod(slot, "lowFloor")).isEqualTo(Boolean.TRUE);
		assertThat((String)ReflectionTestUtils.invokeMethod(slot, "vehicleNo")).isEqualTo("1618");
		assertThat((Integer)ReflectionTestUtils.invokeMethod(slot, "remainingStopCount")).isEqualTo(2);
	}

	@Test
	@DisplayName("BIMS busStopList 응답은 정류장 마스터 페이지로 파싱한다")
	void parseBusStopPage() {
		BusanBimsClient client = new BusanBimsClient(
			new RestTemplateBuilder(),
			new BusanBimsProperties("https://apis.data.go.kr/6260000/BusanBIMS", "key",
				Duration.ofSeconds(5), Duration.ofSeconds(5)));

		BusanBimsBusStopPage page = ReflectionTestUtils.invokeMethod(client, "parseBusStopPage", """
			<response>
			  <body>
			    <items>
			      <item>
			        <bstopid>178700302</bstopid>
			        <bstopnm>다대현대아파트</bstopnm>
			        <arsno>10175</arsno>
			        <gpsx>128.977167399072</gpsx>
			        <gpsy>35.062268914248</gpsy>
			        <stoptype>일반</stoptype>
			      </item>
			    </items>
			    <totalCount>8780</totalCount>
			  </body>
			</response>
			""", 1, 1000);

		assertThat(page.totalCount()).isEqualTo(8780);
		assertThat(page.pageNo()).isEqualTo(1);
		assertThat(page.numOfRows()).isEqualTo(1000);
		assertThat(page.busStops()).hasSize(1);
		assertThat(page.busStops().get(0).stopId()).isEqualTo("178700302");
		assertThat(page.busStops().get(0).stopName()).isEqualTo("다대현대아파트");
		assertThat(page.busStops().get(0).arsNo()).isEqualTo("10175");
		assertThat(page.busStops().get(0).lng()).isEqualTo(128.977167399072);
		assertThat(page.busStops().get(0).lat()).isEqualTo(35.062268914248);
		assertThat(page.busStops().get(0).stopType()).isEqualTo("일반");
	}

	private Element item(String xml) throws Exception {
		DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
		factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
		return factory.newDocumentBuilder()
			.parse(new InputSource(new StringReader(xml)))
			.getDocumentElement();
	}
}
