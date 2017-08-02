package com.nextiva.dataplatform.hbase;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.hbase.Cell;
import org.apache.hadoop.hbase.CellUtil;
import org.apache.hadoop.hbase.HBaseConfiguration;
import org.apache.hadoop.hbase.HBaseTestingUtility;
import org.apache.hadoop.hbase.HColumnDescriptor;
import org.apache.hadoop.hbase.HTableDescriptor;
import org.apache.hadoop.hbase.MasterNotRunningException;
import org.apache.hadoop.hbase.TableName;
import org.apache.hadoop.hbase.ZooKeeperConnectionException;
import org.apache.hadoop.hbase.client.Admin;
import org.apache.hadoop.hbase.client.Connection;
import org.apache.hadoop.hbase.client.ConnectionFactory;
import org.apache.hadoop.hbase.client.Put;
import org.apache.hadoop.hbase.client.Result;
import org.apache.hadoop.hbase.client.ResultScanner;
import org.apache.hadoop.hbase.client.Scan;
import org.apache.hadoop.hbase.client.Table;
import org.apache.hadoop.hbase.io.compress.Compression.Algorithm;
import org.apache.hadoop.hbase.util.Bytes;
import org.codehaus.jackson.map.ObjectMapper;
import org.codehaus.jackson.type.TypeReference;
import org.joda.time.format.DateTimeFormat;
import org.joda.time.format.DateTimeFormatter;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import com.google.common.base.Charsets;
import com.google.common.io.Files;
import com.google.common.io.Resources;

public class HBaseTemporalTest {

	private static HBaseTestingUtility hbaseTestingUtility;
	private static Connection connection;
	private static Admin admin = null;

	private static final String TABLE_E = "Temporal_E";
	private static final String TABLE_A = "Temporal_A";
	private static final String CF = "C";
	private static final String SEPARATOR = "|";

	private static ObjectMapper objectMapper;
	private static TypeReference<HashMap<String, Object>> typeRef = new TypeReference<HashMap<String, Object>>() {
	};
	private static DateTimeFormatter dateFormatter = DateTimeFormat.forPattern("yyyy-MM-dd HH:mm:ss.SSSSSSZ");

	@BeforeClass
	public static void setUp() throws Exception {
		Configuration conf = HBaseConfiguration.create();
		hbaseTestingUtility = new HBaseTestingUtility(conf);

		objectMapper = new ObjectMapper();
		System.out.println("Starting mini cluster...");
		try {
			System.out.println("HBase...");
			hbaseTestingUtility.startMiniCluster();
			System.out.println("HBase...C");
		} catch (Exception e) {
			e.printStackTrace();
		}
		System.out.println("Initializing...");
		initialize(hbaseTestingUtility.getConfiguration());
	}

	private static void initialize(Configuration conf) throws IOException, URISyntaxException {

		HBaseTemporalTest.connection = ConnectionFactory.createConnection(conf);
		try {
			admin = connection.getAdmin();
		} catch (MasterNotRunningException e) {
			assertNull("Master is not running", e);
		} catch (ZooKeeperConnectionException e) {
			assertNull("Cannot connect to Zookeeper", e);
		}
		createTable();
		insertTestData();
	}

	private static void createTable() throws IOException {
		System.out.println("Creating table....");
		assertNotNull("HBaseAdmin is not initialized successfully.", admin);
		if (admin != null) {
			HTableDescriptor descriptor = new HTableDescriptor(TableName.valueOf(TABLE_E));
			descriptor.addFamily(
					new HColumnDescriptor(Bytes.toBytes(CF)).setCompressionType(Algorithm.GZ).setMaxVersions(1));
			admin.createTable(descriptor);

			descriptor = new HTableDescriptor(TableName.valueOf(TABLE_A));
			descriptor.addFamily(
					new HColumnDescriptor(Bytes.toBytes(CF)).setCompressionType(Algorithm.GZ).setMaxVersions(1));
			admin.createTable(descriptor);
		} else {
			System.out.println("admin is null...can't create tables");
		}
	}

	private static void insertTestData() throws IOException, URISyntaxException {
		String accountNumber = "287645";

		Table table_e = null;
		Table table_a = null;
		try {
			table_e = connection.getTable(TableName.valueOf(TABLE_E));
			table_a = connection.getTable(TableName.valueOf(TABLE_A));

			List<String> lines = Files.readLines(new File(Resources.getResource("data.txt").toURI()), Charsets.UTF_8);
			for (String line : lines) {
				if (line != null && !"".equals(line.trim()) && !line.trim().startsWith("#")) {
					Map<String, Object> dataMap = objectMapper.readValue(line, typeRef);
					String entityType = dataMap.get("entityType").toString();
					String updatedAt = dataMap.get("updatedAt").toString();
					int id = getId(entityType, dataMap);
					Put put = new Put(Bytes.toBytes(accountNumber + SEPARATOR + entityType + SEPARATOR
							+ getReversedTs(dateFormatter.parseDateTime(updatedAt).getMillis())));
					put.addColumn(Bytes.toBytes(CF), Bytes.toBytes(id), Bytes.toBytes(line));
					table_e.put(put);

					put = new Put(Bytes.toBytes(accountNumber + SEPARATOR + entityType + SEPARATOR + Bytes.toBytes(id)
							+ getReversedTs(dateFormatter.parseDateTime(updatedAt).getMillis())));
					put.addColumn(Bytes.toBytes(CF), Bytes.toBytes(line), new byte[0]);
					table_a.put(put);
				}
			}
		} finally {
			if (table_e != null) {
				table_e.close();
			}
			if (table_a != null) {
				table_a.close();
			}
		}
	}

	@Test
	public void test() throws IOException {

		String dbAsOfDateTime = "2017-07-01 07:00:00.000000-07:00";
		long dbAsOfTs = dateFormatter.parseDateTime(dbAsOfDateTime).getMillis();

		List<String> entities = Arrays.asList(new String[] { "Case", "Department" });
		List<String> attributes = Arrays
				.asList(new String[] { "Case.caseNumber", "Case.statusType", "Department.departmentName" });
		List<String> conditions = Arrays
				.asList(new String[] { "Case.caseNumber EQUALS \"CBS-265423\"", "Case.departmentId EQUALS Department.departmentId" });

		for (String condition : conditions) {
			String[] conditionArr = condition.split(" ");
			String lhs = conditionArr[0];
			String entity = lhs.substring(0, lhs.indexOf("."));
			String attribute = lhs.substring(lhs.indexOf(".") + 1, lhs.length());
			
		}

		Table table = null;
		try {
			table = connection.getTable(TableName.valueOf(TABLE_E));

			Scan scan = new Scan();
			// scan.setStopRow(stopRow)
			ResultScanner scanner = table.getScanner(scan);
			Result result = null;
			while ((result = scanner.next()) != null) {
				String rowKey = Bytes.toString(result.getRow());
				List<Cell> cells = result.listCells();
				if (cells != null && cells.size() > 0) {
					for (Cell cell : cells) {
						Integer columnQualifier = Bytes.toInt(CellUtil.cloneQualifier(cell));
						String columnValue = Bytes.toString(CellUtil.cloneValue(cell));
						System.out.println(String.format("%s-->%d:%s", rowKey, columnQualifier, columnValue));
					}

				}
			}

		} finally {
			if (table != null) {
				table.close();
			}

		}
	}

	private static String getReversedTs(long timestamp) {
		String reversedDateAsStr = Long.toString(Long.MAX_VALUE - timestamp);
		StringBuilder builder = new StringBuilder();
		for (int i = reversedDateAsStr.length(); i < 19; i++) {
			builder.append('0');
		}
		builder.append(reversedDateAsStr);
		return builder.toString();
	}

	private static int getId(String entityType, Map<String, Object> dataMap) {
		if ("Agent".equals(entityType)) {
			return Integer.parseInt(dataMap.get("agentId").toString());
		} else if ("Department".equals(entityType)) {
			return Integer.parseInt(dataMap.get("departmentId").toString());
		} else if ("Case".equals(entityType)) {
			return Integer.parseInt(dataMap.get("caseId").toString());
		} else if ("Message".equals(entityType)) {
			return Integer.parseInt(dataMap.get("messageId").toString());
		}
		return -1;
	}

	@AfterClass
	public static void tearDown() throws Exception {
		deleteTable();
		System.out.println("Shutting down mini cluster...");
		hbaseTestingUtility.shutdownMiniCluster();
	}

	private static void deleteTable() {
		System.out.println("Deleting table...");
		if (admin != null) {
			try {

				admin.disableTable(TableName.valueOf(TABLE_E));
				admin.deleteTable(TableName.valueOf(TABLE_E));

				admin.close();
			} catch (IOException e) {
				assertNull("Exception found deleting the table", e);
			}
		}
	}

}
