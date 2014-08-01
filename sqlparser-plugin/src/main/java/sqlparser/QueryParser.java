package sqlparser;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.json.*;
/*
 * QueryParser class represents a object which can be used to parse build output query strings for database analytics
 * Used for returning a JSON representation of the schema, tables, and query strings of a build
 * @author Ryan Williams
 * @version 1.0
 */
public class QueryParser {

	private JSONObject queryJson;
	private HashMap<String, HashMap<String, ArrayList<String>>> hashResults;
	private String lastFailedQuery;
	private ArrayList<String> allFailedQuerys;
	private ArrayList<Integer> queryTimes;
	private int maxQueryTime;
	private ArrayList<String> maxQueryTimeStrings;

	private String lastUsedTableName;
	private String lastUsedSchemaName;

	/*
	 * Constructs a QueryParser object and creates an empty JSON Object
	 */
	public QueryParser() {
		queryJson = new JSONObject();
		hashResults = new HashMap<String, HashMap<String, ArrayList<String>>>();
		lastFailedQuery = "";
		allFailedQuerys = new ArrayList<String>();
		queryTimes = new ArrayList<Integer>();
		maxQueryTime = 0;
		maxQueryTimeStrings = new ArrayList<String>();
		lastUsedTableName = "";
		lastUsedSchemaName = "";
	}

	/*
	 * Return the last query which was not successfully parsed. Useful in conjuction with output of parseQuery
	 * @return String representation of failed query
	 */
	public String getLastFailedQuery() {
		if (lastFailedQuery == null) {
			return "There is no query to be sent";
		} else {
			return lastFailedQuery;
		}
	}

	/*
	 * Return all querys that have failed to be parsed
	 * @return ArrayList of all querys that were not successfully parsed by parseQuery
	 */
	public ArrayList<String> getAllFailedQuerys() {
		if (allFailedQuerys.isEmpty()) {
			return null;
		} else {
			return allFailedQuerys;
		}
	}

	/*
	 * Return JSONObject representation of the build output
	 * @return JSONObject object representing build output
	 */
	public JSONObject getResults() {
		if (!hashResults.isEmpty()) {
			try {
				for (Map.Entry<String, HashMap<String, ArrayList<String>>> schema : hashResults.entrySet()) {
					JSONObject tableObject = new JSONObject();
					for (Map.Entry<String, ArrayList<String>> table : hashResults.get(schema.getKey()).entrySet()) {
						tableObject.put(table.getKey(), table.getValue());
					}
					queryJson.put(schema.getKey(), tableObject);
				}
				return queryJson;
			} catch (JSONException e) {
				return null;
			}
		} else {
			return null;
		}
	}

	/*
	 * Reset parsing, create a new JSONObject
	 */
	public void resetParsing() {
		hashResults = new HashMap<String, HashMap<String, ArrayList<String>>>();
	}

	/*
	 * Retrieve the max time any query has taken to process based on build times in the query
	 * @return int max time
	 */
	public int getMaxQueryTime() {
		return maxQueryTime;
	}

	/*
	 * Get strings corresponding to the max time of getMaxQueryTime
	 * @return ArrayList of strings matching longest time
	 */
	public ArrayList<String> getLongestQueryTimeString() {
		if (maxQueryTimeStrings.isEmpty()) {
			return null;
		} else {
			return maxQueryTimeStrings;
		}
	}

	/*
	 * Get average query time overall
	 * @return double average time
	 */
	public Double getAverageQueryTime() {
		Double average = 0.0;
		for (int i = 0; i < queryTimes.size(); i++) {
			average += queryTimes.get(i);
		}
		return average / queryTimes.size();
	}

	/*
	 * Process a query string. Assumes string starts with select, insert, or update
	 * @return boolean success or failure
	 */
	public boolean processQuery(String query) {
		try {
			query = query.toLowerCase().trim();
			query = removeGarbage(query);
			String commandWord = getCommandWord(query);
			if (commandWord.equals("select")) {
				String selectquery = query;
				while (selectquery.indexOf("select ") != -1) {
					processSelectQuery(selectquery.trim());
					selectquery = selectquery.substring(selectquery.indexOf("select ") + 7);
					selectquery = (selectquery.indexOf("select ") == -1) ? "" : selectquery.substring(selectquery
							.indexOf("select "));
				}
				checkForWhereStatement(query);
			} else if (commandWord.equals("insert")) {
				processInsertQuery(query.trim());
			} else if (commandWord.equals("update")) {
				processUpdateQuery(query.trim());
			} else {
				lastFailedQuery = query;
				allFailedQuerys.add(query);
				return false;
			}
			processQueryTime(query);
			return true;
		} catch (Exception e) {
			lastFailedQuery = query;
			allFailedQuerys.add(query);
			return false;
		}
	}

	private void processSelectQuery(String query) {
		query = query.substring(7);
		String schemaAndTable = query.substring(query.indexOf(" from ") + 6);
		String schema = schemaAndTable.substring(0, schemaAndTable.indexOf("."));
		String table = schemaAndTable.substring(schemaAndTable.indexOf(".") + 1, (Math.min(schemaAndTable.indexOf(" "),
				schemaAndTable.length()) == -1) ? schemaAndTable.length() : schemaAndTable.indexOf(" "));
		String unprocessedQuerys = query.substring(0, query.indexOf(" from ")).trim();
		ArrayList<String> queryStrings;
		if (unprocessedQuerys.indexOf("(") != -1 && unprocessedQuerys.indexOf(",") == -1) {
			queryStrings = new ArrayList<String>();
			queryStrings.add(unprocessedQuerys.substring(unprocessedQuerys.indexOf("(") + 1,
					unprocessedQuerys.indexOf(")")));
		} else {
			if (unprocessedQuerys.indexOf(".") == -1) {
				queryStrings = parseQueryByComma(unprocessedQuerys);
			} else {
				queryStrings = parseQueryByPeriod(unprocessedQuerys);
			}
		}
		appendToResults(removeNonLetters(schema), removeNonLetters(table), queryStrings);
		lastUsedSchemaName = removeNonLetters(schema);
		lastUsedTableName = removeNonLetters(table);
	}

	private void processInsertQuery(String query) {
		query = query.substring(12);
		String schema = query.substring(0, query.indexOf(".")).trim();
		String table = query.trim().substring(query.indexOf(".") + 1, query.indexOf(" "));
		String unprocessedQuerys = query.substring(query.indexOf(" ") + 1, query.indexOf(" values"));
		ArrayList<String> queryStrings = parseQueryByComma(unprocessedQuerys);
		appendToResults(schema, table, queryStrings);
	}

	private void processUpdateQuery(String query) {
		query = query.substring(7);
		String schema = query.substring(0, query.indexOf(".")).trim();
		String table = query.trim().substring(query.indexOf(".") + 1, query.indexOf(" "));
		String unprocessedQuerys = query.substring(query.indexOf(" set ") + 5).trim();
		ArrayList<String> queryStrings = parseQueryByEqualsSign(unprocessedQuerys);
		appendToResults(schema, table, queryStrings);
	}

	private void processWhere(String query) {
		query = query.substring(7);
		if (query.indexOf("select ") == -1) {
			query = query.substring(query.indexOf(" where ") + 7).trim();
		} else {
			query = query.substring(query.indexOf(" where ") + 7, query.indexOf("select ")).trim();
		}
		if (query.indexOf(";") != -1) {
			query = query.substring(0, query.indexOf(";"));
		}
		query = query.replaceAll("<", "").replaceAll(">", "");
		while (query.indexOf("=") != -1) {
			Character charAfterSign = query.charAt(query.indexOf("=") + 1);
			if (charAfterSign.equals(' ')) {
				if (query.indexOf(" ", query.indexOf("=") + 2) == -1) {
					query = query.substring(0, query.indexOf("="));
				} else {
					query = query.substring(0, query.indexOf("="))
							+ query.substring(query.indexOf(" ", query.indexOf("=") + 2));
				}
			} else {
				if (query.indexOf(" ", query.indexOf("=")) == -1) {
					query = query.substring(0, query.indexOf("="));
				} else {
					query = query.substring(0, query.indexOf("="))
							+ query.substring(query.indexOf(" ",
									(query.indexOf("=") == -1) ? query.length() : query.indexOf("=") + 2));
				}
			}
		}
		ArrayList<String> queryStrings = new ArrayList<String>();
		String queryToAdd;
		while (query.indexOf(" ") != -1) {
			queryToAdd = query.substring(0, query.indexOf(" "));
			if (queryToAdd.indexOf(".") != -1) {
				if (queryToAdd.indexOf(".", queryToAdd.indexOf(".") + 1) != -1) {
					queryToAdd = queryToAdd.substring(queryToAdd.indexOf("."), queryToAdd.indexOf(".") + 1);
				} else {
					queryToAdd = queryToAdd.substring(queryToAdd.indexOf(".") + 1);
				}
			}
			queryStrings.add(removeNonLetters(queryToAdd));
			query = query.substring(query.indexOf(" ") + 1).trim();
		}
		if (query.indexOf(".") != -1) {
			if (query.indexOf(".", query.indexOf(".") + 1) != -1) {
				query = query.substring(query.indexOf(".", query.indexOf(".") + 1));
			} else {
				query = query.substring(query.indexOf(".") + 1);
			}
		}
		if (!query.isEmpty()) {
			queryStrings.add(removeNonLetters(query));
		}
		if (!queryStrings.get(0).isEmpty()) {
			appendToResults(lastUsedSchemaName, lastUsedTableName, queryStrings);
		}
	}

	private ArrayList<String> parseQueryByPeriod(String unprocessedQuerys) {
		ArrayList<String> queryStrings = new ArrayList<String>();
		String query;
		if (unprocessedQuerys.indexOf(".", unprocessedQuerys.indexOf(".") + 1) == -1) {
			queryStrings = parseQueryByComma(unprocessedQuerys.substring(unprocessedQuerys.indexOf(",") + 1));
			queryStrings.add(unprocessedQuerys.substring(unprocessedQuerys.indexOf(".") + 1,
					unprocessedQuerys.indexOf(",")).trim());
		} else {
			while (unprocessedQuerys.indexOf(".") != -1) {
				unprocessedQuerys = unprocessedQuerys.substring(unprocessedQuerys.indexOf(".") + 1);
				int spaceLoc = unprocessedQuerys.indexOf(" ");
				int commaLoc = unprocessedQuerys.indexOf(",");
				int endingLoc;
				if (spaceLoc == -1 && commaLoc == -1) {
					endingLoc = unprocessedQuerys.length();
				} else if (commaLoc == -1) {
					endingLoc = spaceLoc;
				} else if (spaceLoc == -1) {
					endingLoc = commaLoc;
				} else {
					endingLoc = Math.min(spaceLoc, commaLoc);
				}
				query = unprocessedQuerys.substring(0, endingLoc);
				queryStrings.add(query);
			}
		}
		return queryStrings;
	}

	private ArrayList<String> parseQueryByComma(String unprocessedQuerys) {
		ArrayList<String> queryStrings = new ArrayList<String>();
		String query;
		while (unprocessedQuerys.indexOf(",") != -1) {
			query = unprocessedQuerys.substring(0, unprocessedQuerys.indexOf(","));
			queryStrings.add(query.trim());
			unprocessedQuerys = unprocessedQuerys.substring(unprocessedQuerys.indexOf(",") + 1);
		}
		queryStrings.add(unprocessedQuerys.trim());
		return queryStrings;
	}

	private ArrayList<String> parseQueryByEqualsSign(String unprocessedQuerys) {
		ArrayList<String> queryStrings = new ArrayList<String>();
		String query;
		while (unprocessedQuerys.indexOf(",") != -1) {
			query = unprocessedQuerys.substring(0, unprocessedQuerys.indexOf("="));
			queryStrings.add(query);
			unprocessedQuerys = unprocessedQuerys.substring(unprocessedQuerys.indexOf(",") + 1).trim();
		}
		queryStrings.add(unprocessedQuerys.substring(0, unprocessedQuerys.indexOf("=")));
		return queryStrings;
	}

	private void appendToResults(String schema, String table, ArrayList<String> queryStrings) {
		if (table.indexOf("(") != -1) {
			table = table.substring(0, table.indexOf("("));
		}
		if (queryStrings.get(0).indexOf(".") != -1) {
			queryStrings = removeExcessQueries(queryStrings, table);
		}
		if (hashResults.containsKey(schema)) {
			if (hashResults.get(schema).containsKey(table)) {
				Set<String> uniqueQuerys = new HashSet<String>(hashResults.get(schema).get(table));
				uniqueQuerys.addAll(queryStrings);
				hashResults.get(schema).put(table, new ArrayList<String>(uniqueQuerys));
			} else {
				HashMap<String, ArrayList<String>> tablesAndQueries = hashResults.get(schema);
				tablesAndQueries.put(table, queryStrings);
				hashResults.put(schema, tablesAndQueries);
			}
		} else {
			HashMap<String, ArrayList<String>> tablesAndQueries = new HashMap<String, ArrayList<String>>();
			tablesAndQueries.put(table, queryStrings);
			hashResults.put(schema, tablesAndQueries);
		}
	}

	private String getCommandWord(String query) {
		return query.substring(0, query.indexOf(" "));
	}

	private ArrayList<String> removeExcessQueries(ArrayList<String> queryArray, String table) {
		for (int i = 0; i < queryArray.size(); i++) {
			if (queryArray.get(i).indexOf(".") != -1) {
				queryArray.remove(i);
			}
		}
		return queryArray;
	}

	private void processQueryTime(String query) {
		// Check if a time is available
		// If it fails, it has no effect on the overall success of the query
		// parsing, so we don't want to throw an exception or
		// track the query as a failure
		try {
			if (query.indexOf("ms.") != -1) {
				Integer queryTime = Integer.parseInt(query.substring(query.indexOf(";") + 2, query.indexOf("ms.") - 1));
				queryTimes.add(queryTime);
				if (queryTime > maxQueryTime) {
					maxQueryTime = queryTime;
					maxQueryTimeStrings.add(query);
				}
			}
		} catch (Exception e) {
			return;
		}
	}

	private void checkForWhereStatement(String query) {
		try {
			if (query.indexOf(" where ") != -1) {
				processWhere(query);
			}
		} catch (Exception e) {
			return;
		}
	}

	private String removeNonLetters(String str) {
		return str.replaceAll("[^a-zA-Z_]", "");
	}

	private String removeGarbage(String query) {
		query = query.replaceAll("union ", "").replaceAll("all ", "").replaceAll("inner ", "").replaceAll("join ", "")
				.replaceAll("is ", "").replaceAll("not ", "").replaceAll("null ", "").replaceAll("(NOLOCK)", "")
				.replaceAll("and ", "").replaceAll("getdate\\(\\)", "").replaceAll("between ", "")
				.replaceAll("order ", "").replaceAll("by ", "").replaceAll("asc ", "").replaceAll("in ", "");
		return query;
	}
}
