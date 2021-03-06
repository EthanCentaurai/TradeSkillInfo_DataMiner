import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLConnection;
import java.util.ArrayList;
import java.util.Map;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;


class Combine
{
	public String profession;
	public int spell;

	public int createsId = 0;
	public String skill = "";
	public String reagents = "";
	public int recipeId = 0;
	public int yield = 0;
	public int itemid = 0;
	public int usedBy = 0;

	public Combine(int spell, String profession)
	{
		this.profession = profession;
		this.spell = spell;
	}

	// [spell] = createsId|skill|reagents|recipeId|yield|itemId
	public String toString()
	{
		if (skill.length() > 1)
		{
			int productId = (createsId > 0) ? createsId :
				((usedBy > 0) ? usedBy : 130206);	// If there is no suitable item created by the spell, just use "Informative Note" as stub

			String temp = "\t[" + spell + "] = \"" + productId + "|" + skill + "|" + reagents;

			if (itemid != 0)
				return temp + "|" + (recipeId != 0 ? recipeId : "") + "|" + (yield != 0 ? yield : "") + "|" + itemid + "\",\n";

			if (yield != 0)
				return temp + "|" + (recipeId != 0 ? recipeId : "") + "|" + yield + "\",\n";

			if (recipeId != 0)
				return temp + "|" + recipeId + "\",\n";

			return temp + "\",\n";
		}

		return "";
	}
}


class Recipe
{
	public int id;
	public String profession;
	public Object result;
	public String source;
	public String price;
	public String factionrank;

	public Recipe(int id, String profession, Object result, String source)
	{
		this.id = id;
		this.profession = profession;
		this.result = result;
		this.source = source;
		price = "";
		factionrank = "";
	}

	public String toString() {
		return "\t[" + id + "] = \"" + result + "|" + source + "\",";
	}
}


class WowHeadParser
{
	public static String extractListViewId(String line)
	{
		Pattern idPattern = Pattern.compile("new Listview\\(\\{template: '.*?', id: '(.*?)',");
		Matcher m = idPattern.matcher(line);
		if (!m.find())
			return null;

		return m.group(1);
	}

	public static JSONArray extractListViewDataArray(String line) throws JSONException
	{
		Pattern dataPattern = Pattern.compile("data: (\\[.*\\])\\}\\);");
		Matcher m = dataPattern.matcher(line);
		if (!m.find())
			return new JSONArray();

		return new JSONArray(m.group(1));
	}

	public static void fillCombineReagents(Combine combine, JSONObject row, Map<Integer, String> components) throws JSONException
	{
		JSONArray reagents = row.optJSONArray("reagents");
		if (reagents == null)
			return;

		String regStr = "";
		for (int j = 0; j < reagents.length(); j++)
		{
			JSONArray reagent = reagents.getJSONArray(j);

			int component = reagent.getInt(0);
			int amount = reagent.getInt(1);

			// TODO: get component source instead of defaulting to "vendor"
			components.put(component, "V");

			regStr = regStr + component + ":" + amount + " ";
		}

		combine.reagents = regStr.trim();
	}

	public static void fillCombineSkill(Combine combine, JSONObject row) throws JSONException
	{
		JSONArray colors = row.optJSONArray("colors");
		if (colors == null)
			return;

		int orange = colors.getInt(0);
		int yellow = colors.getInt(1);
		int green  = colors.getInt(2);
		int grey   = colors.getInt(3);

		if (green == 0) { green = grey; }
		if (yellow == 0) { yellow = green; }
		if (orange == 0) { orange = yellow; }

		String profLetter = TSInfo.getProfessionLetter(combine.profession);
		combine.skill = profLetter + orange + "/" + yellow + "/" + green + "/" + grey;
	}

	public static void fillCombineYield(Combine combine, JSONObject row) throws JSONException
	{
		JSONArray creates = row.optJSONArray("creates");
		if (creates == null)
			return;

		combine.createsId = creates.optInt(0);
		combine.yield = creates.getInt(1);
	}

	public static void fillCombineSource(Combine combine, JSONObject row, SortedMap<Integer, Recipe> recipes) throws JSONException
	{
		combine.recipeId = row.optInt("id");
		if (combine.recipeId <= 0)
			return;

		JSONArray sources = row.optJSONArray("source");

		String source = "U";	// New letter for Unavailable recipes
		if (sources != null)
		{
			switch (sources.getInt(0))
			{
				case 1  : source = "C"; break; // crafted by a player
				case 2  : source = "D"; break; // dropped as loot
				case 4  : source = "Q"; break; // quest reward
				case 5  : source = "V"; break; // bought from vendor
				case 6  : source = "T"; break; // learned from trainer
				case 10 : source = "R"; break; // starter recipe
				case 12 : source = "A"; break; // achievement reward
				case 16 : source = "F"; break; // fished up
				case 21 : source = "P"; break; // pickpocketed
			}
		}

		Recipe r = new Recipe(combine.recipeId, combine.profession, combine.spell, source);
		recipes.put(r.id, r);
	}
}


public class TSInfo
{
	public SortedMap<Integer, Combine> combines;
	public SortedMap<Integer, String> components;
	public SortedMap<Integer, Recipe> recipes;
	public ArrayList<Integer> spells;
	public boolean caughtExceptions = false;

	final static String[] professions = {
		"Alchemy",
		"Blacksmithing",
		"Cooking",
		"Enchanting",
		"Engineering",
//		"First Aid",
		"Inscription",
		"Jewelcrafting",
		"Leatherworking",
		"Mining",
		"Tailoring",
	};

	public TSInfo()
	{
		combines = new TreeMap<Integer, Combine>();
		components = new TreeMap<Integer, String>();
		recipes = new TreeMap<Integer, Recipe>();
		spells = new ArrayList<Integer>();
	}

	public void addCombine(Combine newCombine)
	{
		int id = newCombine.spell;
		if (combines.get(id) == null)
			combines.put(id, newCombine);
	}

	public static int getProfessionId(String profession)
	{
		switch (profession) {
			case "Alchemy"        : return 171;
			case "Blacksmithing"  : return 164;
			case "Cooking"        : return 185;
			case "Enchanting"     : return 333;
			case "Engineering"    : return 202;
//			case "First Aid"      : return 129;
			case "Inscription"    : return 773;
			case "Jewelcrafting"  : return 755;
			case "Leatherworking" : return 165;
			case "Mining"         : return 186;
			case "Tailoring"      : return 197;
		}
		return 0;
	}

	public static String getProfessionLetter(String profession)
	{
		String temp = "";
		switch (profession) {
			case "Alchemy"        : temp = "A"; break;
			case "Blacksmithing"  : temp = "B"; break;
			case "Cooking"        : temp = "W"; break;
			case "Enchanting"     : temp = "D"; break;
			case "Engineering"    : temp = "E"; break;
//			case "First Aid"      : temp = "X"; break;
			case "Inscription"    : temp = "I"; break;
			case "Jewelcrafting"  : temp = "J"; break;
			case "Leatherworking" : temp = "L"; break;
			case "Mining"         : temp = "Y"; break;
			case "Tailoring"      : temp = "T"; break;
		}
		return temp;
	}

	public void buffedProcessRow(JSONObject row) throws JSONException
	{
		int spell = row.getInt("id");
		Combine combine = combines.get(spell);
		if (combine == null)
			return;				// New combine. But practice shows that combines that are only on Buffed and not
								// on WowHead seem to be not in game (old info left from betas, PTRs, etc).
								// So we just ignore it.

		JSONObject p = row.optJSONObject("p");
		if (p == null)
			return;

		if (combine.usedBy == 0)
			combine.usedBy = p.getInt("id");
	}

	public void addInfoFromBuffed(String profession)
	{
		String prefix = "var bt = new Btabs(";
		String suffix = ");bt.init();</script>";
		try
		{
			URL url = new URL("http://wowdata.buffed.de/spell/profession/" + getProfessionId(profession));
			URLConnection bc = url.openConnection();
			BufferedReader in = new BufferedReader(new InputStreamReader(bc.getInputStream()));

			String line;
			while ((line = in.readLine()) != null) {
				if (line.startsWith(prefix) && line.endsWith(suffix)) {
					JSONArray jArray = new JSONArray(line.substring(prefix.length(), line.length() - suffix.length()));
					if (jArray.length() > 0) {
						JSONObject jObject = jArray.optJSONObject(0);
						if (jObject != null) {
							JSONArray rows = jObject.optJSONArray("rows");
							if (rows != null) {
								for (int i = 0; i < rows.length(); i++)
									buffedProcessRow(rows.getJSONObject(i));
							}
						}
					}
					break;
				}
			}
			in.close();
		}
		catch (Exception e)
		{
			caughtExceptions = true;
			e.printStackTrace();
		}
	}

	public void scanWowHead(String profession)
	{
		// For some reason Pandaren Ways are not included on the main cooking page, we need to scan them seperately.
		if (!profession.contains("pandaren-cuisine"))
			spells.clear();

		try
		{
			URL url = new URL("https://www.wowhead.com/spells" + profession);
			URLConnection bc = url.openConnection();
			bc.addRequestProperty("User-Agent", "Mozilla");
			bc.setReadTimeout(20000);
			bc.setConnectTimeout(20000);

			BufferedReader in = new BufferedReader(new InputStreamReader(bc.getInputStream()));

			String prefix = "var listviewspells = ";
			String suffix = "}];";

			String line;
			while ((line = in.readLine()) != null)
			{
				if (!line.startsWith(prefix) || !line.endsWith(suffix))
					continue;

				line = line.substring(prefix.length());

				// WoWhead have a duplicate "quality" key in their JSON array. strip it out.
				line = line.replaceAll(",quality:0,", ",");
				line = line.replaceAll(",quality:1,", ",");
				line = line.replaceAll(",quality:2,", ",");
				line = line.replaceAll(",quality:3,", ",");
				line = line.replaceAll(",quality:4,", ",");
				line = line.replaceAll(",quality:5,", ",");

				JSONArray rows = new JSONArray(line);

				for (int i = 0; i < rows.length(); i++)
				{
					JSONObject row = rows.getJSONObject(i);

					int id = row.optInt("id");
					if (id != 0)
						spells.add(id);
				}

				break;
			}

			in.close();
		}
		catch (Exception e)
		{
			caughtExceptions = true;
			e.printStackTrace();
		}
	}

	public void readProfessionFromWowHead(String profession)
	{
		int current = 0;
		int total = spells.size();

		for (Integer spell : spells)
		{
//			Thread.sleep(100);

			current = current + 1;
			System.out.println("  Scanning " + current + " of " + total + ". (" + spell + ")\r");

			try
			{
				readSpellDetailsFromWowHead(spell, profession);
				readSpellDetailsFromWowDB(spell, profession); // WoWDB has some additional info that WowHead does not, so let's add it.
			}
			catch (Exception e)
			{
				caughtExceptions = true;
				e.printStackTrace();
			}
		}

		System.out.println("  Done!                            \n");
	}

// Components
// [itemid] = "source"
// Source
//  V = Vendor(5)
//  D = Dropped(2)
//  C = Crafted(1)
//  M = Mined
//  H = Herbalism
//  S = Skinned
//  F = Fished(16)
//  E = Disenchanted
//  G = Gathered
//  P = Pickpocketed(21)

// 1 Crafted
// 2 Dropped
// 4 Quest Reward
// 5 Vendor
// 6 Trainer
// 7 Discovery
// 10 Starter Recipe
// 12 Achievement Reward
// 16 Fished
// 21 Pickpocketed

	public void readSpellDetailsFromWowDB(int spell, String profession) throws MalformedURLException, IOException, JSONException
	{
		URL url = new URL("https://www.wowdb.com/api/spell/" + spell);
		URLConnection bc = url.openConnection();
		bc.addRequestProperty("User-Agent", "Mozilla");
		bc.setReadTimeout(20000);
		bc.setConnectTimeout(20000);

		InputStreamReader br = new InputStreamReader(bc.getInputStream());
		BufferedReader in = new BufferedReader(br);

		String line;
		while ((line = in.readLine()) != null)
		{
			Combine combine = combines.get(spell);
			if (combine == null)
				return;				// New combine. But practice shows that combines that are only on Buffed and not
									// on WowHead seem to be not in game (old info left from betas, PTRs, etc).
									// So we just ignore it.

			if (combine.createsId == 0) {
				System.out.println("    Scanning WoWDB for additional info...");

				// WoWDB API returns JSON surrounded with parentheses which must be stripped.
				line = line.substring(1);
				JSONObject rows = new JSONObject(line);
//				System.out.println(rows);

				JSONArray row = rows.getJSONArray("Effects");
//				System.out.println(row);

				JSONObject effects = row.getJSONObject(0);
//				System.out.println(effects);

				combine.createsId = effects.getInt("Item");
				combine.yield = effects.getInt("BasePoints");
//				System.out.println(combine.createsId);
			}
		}

		in.close();
	}

	public void readSpellDetailsFromWowHead(int spell, String profession) throws MalformedURLException, IOException, JSONException
	{
		URL url = new URL("https://www.wowhead.com/spell=" + spell);
		URLConnection bc = url.openConnection();
		bc.addRequestProperty("User-Agent", "Mozilla");
		bc.setReadTimeout(20000);
		bc.setConnectTimeout(20000);

		InputStreamReader br = new InputStreamReader(bc.getInputStream());
		BufferedReader in = new BufferedReader(br);

		String prefix = "new Listview({";
		String suffix = "});";

		Combine combine = new Combine(spell, profession);

		String line;
		while ((line = in.readLine()) != null)
		{
			if (!line.startsWith(prefix) || !line.endsWith(suffix))
				continue;

			// Unfortunately, we can't just parse this JSON, because it may contain some JavaScript constructs mixed in.
			// So we first extract the fields we need with regex and then parse.
			String id = WowHeadParser.extractListViewId(line);
			if ("recipes".equals(id))
			{
//				System.out.println("    'recipes' exists for " + spell);
				JSONObject row = WowHeadParser.extractListViewDataArray(line).getJSONObject(0);

				WowHeadParser.fillCombineReagents(combine, row, components);
				WowHeadParser.fillCombineSkill(combine, row);
				WowHeadParser.fillCombineYield(combine, row);
			}
			else if ("taught-by-item".equals(id))
			{
//				System.out.println("    'taught-by-item' exists for " + spell);
				JSONArray dataArray = WowHeadParser.extractListViewDataArray(line);

				for (int i = 0; i < dataArray.length(); i++)
				{
					JSONObject row = dataArray.getJSONObject(i);
					WowHeadParser.fillCombineSource(combine, row, recipes);
				}
			}
			else if ("used-by-item".equals(id))
			{
//				System.out.println("    'used-by-item' exists for " + spell);
				JSONObject row = WowHeadParser.extractListViewDataArray(line).getJSONObject(0);
				combine.usedBy = row.getInt("id");
			}
		}

		addCombine(combine);
		in.close();
	}

	public void writeToFile(String filename)
	{
		try
		{
			BufferedWriter out = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(new File(filename))));

			out.write("\nTradeskillInfo.vars.specialcases = {\n");
//			for (Object item : specialcases) {
//				out.write(item + "\n");
//			}
			out.write("}\n");

			out.write("\nTradeskillInfo.vars.combines = {\n");
			for (String profession : professions)
			{
				out.write("\n\t--[[ " + profession + " ]]--\n");
				for (Combine combine : combines.values())
					if (profession.equals(combine.profession))
						out.write(combine.toString());
			}
			out.write("}\n");

			out.write("\nTradeskillInfo.vars.components = {\n");
			for (int id : components.keySet())
			{
				out.write("\t[" + id + "] = \"" + components.get(id) + "\",\n");
			}
			out.write("}\n");

			out.write("\nTradeskillInfo.vars.recipes = {\n");
			for (String profession : professions)
			{
				out.write("\n\t--[[ " + profession + " ]]--\n");
				for (Recipe recipe : recipes.values())
					if (profession.equals(recipe.profession))
						out.write(recipe + "\n");
			}
			out.write("}\n");

			out.close();
		}
		catch (Exception e)
		{
			caughtExceptions = true;
			e.printStackTrace();
		}
	}


	public static void main(String argv[])
	{
		long starttime = System.currentTimeMillis();

		System.out.println("Beginning scan...");
		TSInfo tsi = new TSInfo();

		for (String profession : professions) {
			System.out.println("Scanning " + profession + "...");

			if (profession.equals("Cooking")) {
				// For some reason Pandaren Ways are not included on the main cooking page, we need to scan them seperately.
				tsi.scanWowHead("/secondary-skills/cooking/live-only:on?filter=20;1;0#0+17+20");
				tsi.scanWowHead("/secondary-skills/cooking/pandaren-cuisine/live-only:on?filter=20;1;0#0+17+20");
			}
			else if (!profession.equals("Cooking")) {
				// Exclude PTR recipes, filter only for recipes that have reagents.
				tsi.scanWowHead("/professions/" + profession.toLowerCase() + "/live-only:on?filter=20;1;0#0+17+20");
			}

			tsi.readProfessionFromWowHead(profession);
		}

		System.out.println("Saving data to Data.lua...");
		tsi.writeToFile("Data.lua");

		long difftime = System.currentTimeMillis() - starttime;

//		long hours = TimeUnit.MILLISECONDS.toHours(difftime);
//		difftime -= TimeUnit.HOURS.toMillis(hours);

		long minutes = TimeUnit.MILLISECONDS.toMinutes(difftime);
		difftime -= TimeUnit.MINUTES.toMillis(minutes);

		long seconds = TimeUnit.MILLISECONDS.toSeconds(difftime);

//		String formattedtime = String.format("Scan completed in %02d hours, %02d minutes, %02d seconds.", hours, minutes, seconds);
		String formattedtime = String.format("Scan completed in %02d minutes and %02d seconds.", minutes, seconds);
		System.out.println("Scan completed in " + formattedtime);

		if (tsi.caughtExceptions)
			System.out.println("Some exceptions encountered.");
	}
}
