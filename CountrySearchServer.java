import com.sun.net.httpserver.*;
import java.io.*;
import java.net.*;
import java.nio.file.*;
import java.util.*;

//Make the website look more 2000s like

public class CountrySearchServer {

    private static List<String> headers = new ArrayList<>();
    private static List<Map<String, String>> countries = new ArrayList<>();
    
    private static final String CSP = "script-src 'self' 'unsafe-inline' https://static.cloudflareinsights.com";
    
    private static final Map<String, String> DISPLAY_FIELDS = new LinkedHashMap<>();

    static {
        DISPLAY_FIELDS.put("Country", "Country");
    // CHANGED: now uses colon format
        DISPLAY_FIELDS.put("Government: Country name: conventional long form", "Official Name");
        DISPLAY_FIELDS.put("Geography: Location", "Location");
    // CHANGED: colon instead of dash
        DISPLAY_FIELDS.put("Geography: Area: total ", "Total Area");
    // CHANGED: colon instead of dash
        DISPLAY_FIELDS.put("People and Society: Population: total", "Population");
    // CHANGED: colon instead of dash
        DISPLAY_FIELDS.put("Government: Capital: name", "Capital");
        DISPLAY_FIELDS.put("Government: Government type", "Government Type");
        DISPLAY_FIELDS.put("People and Society: Languages", "Languages");
        DISPLAY_FIELDS.put("People and Society: Religions", "Religions");
    // CHANGED: colon instead of dash
        DISPLAY_FIELDS.put("People and Society: Nationality: noun", "Nationality");
    // CHANGED: colon instead of dash
        DISPLAY_FIELDS.put("People and Society: Life expectancy at birth: total population", "Life Expectancy");
    // CHANGED: colon instead of dash
        DISPLAY_FIELDS.put("People and Society: Median age: total", "Median Age");
        DISPLAY_FIELDS.put("Geography: Climate", "Climate");
        DISPLAY_FIELDS.put("Geography: Natural resources", "Natural Resources");
        DISPLAY_FIELDS.put("Economy: Economic overview", "Economic Overview");
    // CHANGED: now shows most recent year
        DISPLAY_FIELDS.put("Economy: Real GDP per capita: Real GDP per capita 2024", "GDP Per Capita");
        DISPLAY_FIELDS.put("Economy: Industries", "Industries");
        DISPLAY_FIELDS.put("Economy: Exports - partners", "Export Partners");
        DISPLAY_FIELDS.put("Economy: Imports - partners", "Import Partners");
        DISPLAY_FIELDS.put("Government: Independence", "Independence");
    // CHANGED: new format for anthem
        DISPLAY_FIELDS.put("Government: National anthem(s): title", "National Anthem");
        DISPLAY_FIELDS.put("Government: Flag description", "Flag Description");
        DISPLAY_FIELDS.put("Military and Security: Military expenditures", "Military Expenditures");
        DISPLAY_FIELDS.put("Environment: Environment - current issues", "Environmental Issues");
        // REMOVED: url - no longer in new CSV
    }

    public static void main(String[] args) throws Exception {
        // Load CSV data
        System.out.println("Loading countries.csv...");
        loadCSV("countries.csv");
        System.out.println("Loaded " + countries.size() + " countries.");

        // Railway sets a PORT environment variable - always use it
        String portEnv = System.getenv("PORT");
        int port = (portEnv != null) ? Integer.parseInt(portEnv) : 8080;

        HttpServer server = HttpServer.create(new InetSocketAddress("0.0.0.0", port), 0);
        
        server.createContext("/", new HomeHandler());
        server.createContext("/search", new SearchHandler());
        server.createContext("/api/search", new APISearchHandler());
        
        server.setExecutor(null);
        server.start();
        
        System.out.println("==========================================");
        System.out.println("  Server is LIVE on port " + port + "!");
        System.out.println("==========================================");
    }

    static class HomeHandler implements HttpHandler {
        public void handle(HttpExchange exchange) throws IOException {
            String html = getHomePage();
            byte[] bytes = html.getBytes(java.nio.charset.StandardCharsets.UTF_8);
            // Updated this to fix some crud
            exchange.getResponseHeaders().set("Content-Security-Policy", CSP);
            exchange.sendResponseHeaders(200, bytes.length);
            OutputStream os = exchange.getResponseBody();
            os.write(bytes);
            os.close();
        }
    }

    static class SearchHandler implements HttpHandler {
        public void handle(HttpExchange exchange) throws IOException {
            String query = "";
            if (exchange.getRequestURI().getQuery() != null) {
                String[] params = exchange.getRequestURI().getQuery().split("&");
                for (String param : params) {
                    String[] kv = param.split("=");
                    if (kv[0].equals("q")) {
                    query = URLDecoder.decode(kv[1], "UTF-8");
                    }    
                }
            }
            exchange.getResponseHeaders().set("Content-Security-Policy", CSP);
            String html = getSearchResultsPage(query);
            exchange.getResponseHeaders().set("Content-Type", "text/html; charset=UTF-8");
            byte[] bytes = html.getBytes(java.nio.charset.StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, bytes.length);
            OutputStream os = exchange.getResponseBody();
            os.write(bytes);
            os.close();
        }
    }

    static class APISearchHandler implements HttpHandler {
        public void handle(HttpExchange exchange) throws IOException {
            String query = "";
            if (exchange.getRequestURI().getQuery() != null) {
                String[] params = exchange.getRequestURI().getQuery().split("&");
                for (String param : params) {
                    String[] kv = param.split("=");
                    if (kv[0].equals("q")) {
                        query = URLDecoder.decode(kv[1], "UTF-8");
                    }
                }
            }
    
            List<Map<String, String>> results = searchCountry(query);
            String json = resultsToJSON(results);
            exchange.getResponseHeaders().set("Content-Security-Policy", CSP);
            exchange.getResponseHeaders().set("Content-Type", "application/json; charset=UTF-8");
            byte[] bytes = json.getBytes(java.nio.charset.StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, bytes.length);
            OutputStream os = exchange.getResponseBody();
            os.write(bytes);
            os.close();
        }
    }

    // The actual HTML Page
    private static String getHomePage() {
        return "<!DOCTYPE html>\n" +
            "<html lang='en'>\n" +
            "<head>\n" +
            "    <meta charset='UTF-8'>\n" +
            "    <meta name='viewport' content='width=device-width, initial-scale=1.0'>\n" +
            "    <title>Natlas</title>\n" +
            "    <style>\n" +
            "        * { margin: 0; padding: 0; box-sizing: border-box; }\n" +
            "        body {\n" +
            "            font-family: ui-serif, 'Charter', 'Bitstream Charter', Georgia, Cambria, 'Times New Roman', Times, serif;\n" +
            "            background: linear-gradient(450deg, #1e1e1e 35%, #ababab 100%);\n" +
            "            min-height: 100vh;\n" +
            "            display: flex;\n" +
            "            align-items: center;\n" +
            "            justify-content: center;\n" +
            "            padding: 15px;\n" +
            "        }\n" +
            "        .container {\n" +
            "            background: white;\n" +
            "            border-radius: 12px;\n" +
            "            box-shadow: 0 20px 60px rgba(0,0,0,0.3);\n" +
            "            padding: 60px 40px;\n" +
            "            max-width: 600px;\n" +
            "            width: 100%;\n" +
            "            text-align: center;\n" +
            "        }\n" +
            "        h1 {\n" +
            "            color: #000000;\n" +
            "            font-size: 2.5em;\n" +
            "            margin-bottom: 10px;\n" +
            "        }\n" +
            "        .subtitle {\n" +
            "            color: #949494;\n" +
            "            font-size: 1.1em;\n" +
            "            margin-bottom: 40px;\n" +
            "        }\n" +
            "        .search-box {\n" +
            "            position: relative;\n" +
            "            margin-bottom: 25px;\n" +
            "        }\n" +
            "        input[type='text'] {\n" +
            "            width: 100%;\n" +
            "            padding: 18px 24px;\n" +
            "            font-size: 1.1em;\n" +
            "            border: 2px solid #e2e8f0;\n" +
            "            border-radius: 12px;\n" +
            "            outline: none;\n" +
            "            margin-bottom: 20px;\n" +
            "            transition: all 0.3s;\n" +
            "        }\n" +
            "        input[type='text']:focus {\n" +
            "            border-color: #a3a3a3;\n" +
            "            margin-top: 30px;\n" +
            "            box-shadow: 0 0 0 3px rgba(230, 230, 230, 0.1);\n" +
            "        }\n" +
            "        button {\n" +
            "            width: 100%;\n" +
            "            padding: 18px;\n" +
            "            font-size: 1.1em;\n" +
            "            font-weight: 600;\n" +
            "            color: white;\n" +
            "            background: linear-gradient(135deg, #1e1e1e 0%, #ababab 100%);\n" +
            "            border: none;\n" +
            "            border-radius: 12px;\n" +
            "            cursor: pointer;\n" +
            "            transition: transform 0.2s, box-shadow 0.2s;\n" +
            "        }\n" +
            "        button:hover {\n" +
            "            transform: translateY(-2px);\n" +
            "            box-shadow: 0 10px 20px rgba(123, 123, 123, 0.3);\n" +
            "        }\n" +
            "        button:active {\n" +
            "            transform: translateY(0);\n" +
            "        }\n" +
            "        .globe {\n" +
            "            font-size: 4em;\n" +
            "            margin-bottom: 20px;\n" +
            "            animation: float 4s ease-in-out infinite;\n" +
            "        }\n" +
            "        @keyframes float {\n" +
            "            0%, 100% { transform: translateY(0px); }\n" +
            "            50% { transform: translateY(-20px); }\n" +
            "        }\n" +
            "        .info {\n" +
            "            color: #949494;\n" +
            "            font-size: 0.95em;\n" +
            "        }\n" +
            "        .claus {\n" +
            "            color: #000000;\n" +
            "            font-size: 0.60em;\n" +
            "        }\n" +  
            "    </style>\n" +
            "</head>\n" +
            "<body>\n" +
            "    <div class='container'>\n" +
            "        <div class='globe'>?</div>\n" +
            "        <h1>National Atlas</h1>\n" +
            "        <p class='subtitle'>Explore detailed information about any country, region, or territory, from Italy to Canada; Using backed information from the CIA.</p>\n" +
            "        <form action='/search' method='GET' class='search-box'>\n\n" +
            "            <input type='text' name='q' placeholder='Enter country name...' required autofocus>\n" +
            "            <button type='submit'>Search</button>\n" +
            "        </form>\n" +
            "        <p class='info'>Try searching: United States, Germany, Japan, Brazil; Or use abreviation like, ger, united, or braz.</p>\n" +
            "        <p class='info' style='margin-top: 10px;'>" + countries.size() + " countries, regions, or territorys current publicly loaded.</p>\n" +
            "        <p class='claus' style='margin-top: 3px;'>All information is sourced from the Central Intelligence Agency (2024) & Gerald Bauer (Feb 25st, 2026).\nCurrent Web Version:0.0.84\nOriginal Creator - A-Palmer</p>\n" +
            "    </div>\n" +
            "    <script>\n" +
            "        const canvas = document.createElement('canvas');\n" +
            "        canvas.width = 64;\n" +
            "        canvas.height = 64;\n" +
            "        const ctx = canvas.getContext('2d');\n" +
            "        const globes = ['🌍', '🌎', '🌏'];\n" +
            "        let frame = 0;\n" +
            "        \n" +
            "        function updateFavicon() {\n" +
            "            ctx.clearRect(0, 0, 64, 64);\n" +
            "            ctx.font = '56px Arial';\n" +
            "            ctx.textAlign = 'center';\n" +
            "            ctx.textBaseline = 'middle';\n" +
            "            ctx.fillText(globes[frame % globes.length], 32, 36);\n" +
            "            \n" +
            "            const link = document.querySelector(\"link[rel*='icon']\") || document.createElement('link');\n" +
            "            link.type = 'image/x-icon';\n" +
            "            link.rel = 'shortcut icon';\n" +
            "            link.href = canvas.toDataURL();\n" +
            "            document.getElementsByTagName('head')[0].appendChild(link);\n" +
            "            \n" +
            "            frame++;\n" +
            "        }\n" +
            "        \n" +
            "        setInterval(updateFavicon, 500);\n" +
            "        updateFavicon();\n" +
            "    </script>\n" +
            "</body>\n" +
            "</html>";
    }

    private static String getSearchResultsPage(String query) {
        List<Map<String, String>> results = searchCountry(query);
        
        StringBuilder html = new StringBuilder();
        html.append("<!DOCTYPE html>\n");
        html.append("<html lang='en'>\n");
        html.append("<head>\n");
        html.append("    <meta charset='UTF-8'>\n");
        html.append("    <meta name='viewport' content='width=device-width, initial-scale=1.0'>\n");
        html.append("    <title>Natlas - ").append(escapeHtml(query)).append("</title>\n");
        html.append("    <style>\n");
        html.append("        * { margin: 0; padding: 0; box-sizing: border-box; }\n");
        html.append("        body {\n");
        html.append("            font-family: ui-serif, 'Charter', 'Bitstream Charter', Georgia, Cambria, 'Times New Roman', Times, serif;\n");
        html.append("            background: linear-gradient(25deg, #1e1e1e 0%, #ababab 100%);\n");
        html.append("            min-height: 100vh;\n");
        html.append("            padding: 40px 20px;\n");
        html.append("        }\n");
        html.append("        .header {\n");
        html.append("            text-align: center;\n");
        html.append("            color: white;\n");
        html.append("            margin-bottom: 30px;\n");
        html.append("        }\n");
        html.append("        h1 { font-size: 2.5em; margin-bottom: 10px; }\n");
        html.append("        .subtitle { font-size: 1.2em; opacity: 0.9; }\n");
        html.append("        .container {\n");
        html.append("            max-width: 900px;\n");
        html.append("            margin: 0 auto;\n");
        html.append("            background: white;\n");
        html.append("            border-radius: 20px;\n");
        html.append("            box-shadow: 0 20px 60px rgba(0,0,0,0.3);\n");
        html.append("            padding: 40px;\n");
        html.append("        }\n");
        html.append("        .country-card {\n");
        html.append("            margin-bottom: 30px;\n");
        html.append("        }\n");
        html.append("        .country-name {\n");
        html.append("            font-size: 2em;\n");
        html.append("            color: #000000;\n");
        html.append("            margin-bottom: 20px;\n");
        html.append("            padding-bottom: 15px;\n");
        html.append("            border-bottom: 3px solid #4c4c4c;\n");
        html.append("        }\n");
        html.append("        .toggle-container {\n");
        html.append("            margin: 20px 0;\n");
        html.append("            text-align: center;\n");
        html.append("        }\n");
        html.append("        .toggle-button {\n");
        html.append("            display: inline-block;\n");
        html.append("            padding: 12px 24px;\n");
        html.append("            font-size: 1em;\n");
        html.append("            font-weight: 600;\n");
        html.append("            color: white;\n");
        html.append("            background: linear-gradient(135deg, #48bb78 0%, #007e29 100%);\n");
        html.append("            border: none;\n");
        html.append("            border-radius: 8px;\n");
        html.append("            cursor: pointer;\n");
        html.append("            transition: transform 0.2s, box-shadow 0.2s;\n");
        html.append("        }\n");
        html.append("        .toggle-button:hover {\n");
        html.append("            transform: translateY(-2px);\n");
        html.append("            box-shadow: 0 8px 16px rgba(72, 187, 120, 0.3);\n");
        html.append("        }\n");
        html.append("        .toggle-button:active {\n");
        html.append("            transform: translateY(0);\n");
        html.append("        }\n");
        html.append("        .field {\n");
        html.append("            margin-bottom: 15px;\n");
        html.append("            padding: 15px;\n");
        html.append("            background: #f7fafc;\n");
        html.append("            border-radius: 8px;\n");
        html.append("            border-left: 4px solid #a6a6a6;\n");
        html.append("        }\n");
        html.append("        .field-label {\n");
        html.append("            font-weight: 600;\n");
        html.append("            color: #000000;\n");
        html.append("            margin-bottom: 5px;\n");
        html.append("            font-size: 0.9em;\n");
        html.append("            text-transform: uppercase;\n");
        html.append("            letter-spacing: 0.5px;\n");
        html.append("        }\n");
        html.append("        .field-value {\n");
        html.append("            color: #434343;\n");
        html.append("            font-size: 1.05em;\n");
        html.append("            line-height: 1.6;\n");
        html.append("        }\n");
        html.append("        .all-data {\n");
        html.append("            display: none;\n");
        html.append("            margin-top: 20px;\n");
        html.append("            padding-top: 20px;\n");
        html.append("            border-top: 2px dashed #e1e1e1;\n");
        html.append("        }\n");
        html.append("        .all-data.visible {\n");
        html.append("            display: block;\n");
        html.append("        }\n");
        html.append("        .all-data-header {\n");
        html.append("            font-size: 1.3em;\n");
        html.append("            color: #000000;\n");
        html.append("            margin-bottom: 15px;\n");
        html.append("            font-weight: 600;\n");
        html.append("        }\n");
        html.append("        .back-link {\n");
        html.append("            display: inline-block;\n");
        html.append("            color: white;\n");
        html.append("            text-decoration: none;\n");
        html.append("            font-weight: 600;\n");
        html.append("            padding: 10px 20px;\n");
        html.append("            background: rgba(255,255,255,0.2);\n");
        html.append("            border-radius: 8px;\n");
        html.append("            transition: background 0.2s;\n");
        html.append("            margin-bottom: 20px;\n");
        html.append("        }\n");
        html.append("        .back-link:hover {\n");
        html.append("            background: rgba(255,255,255,0.3);\n");
        html.append("        }\n");
        html.append("        .no-results {\n");
        html.append("            text-align: center;\n");
        html.append("            padding: 60px 20px;\n");
        html.append("        }\n");
        html.append("        .no-results h2 {\n");
        html.append("            color: #404040;\n");
        html.append("            font-size: 2em;\n");
        html.append("            margin-bottom: 15px;\n");
        html.append("        }\n");
        html.append("        .no-results p {\n");
        html.append("            color: #989898;\n");
        html.append("            font-size: 1.1em;\n");
        html.append("        }\n");
        html.append("        .multiple-results {\n");
        html.append("            padding: 20px;\n");
        html.append("        }\n");
        html.append("        .result-link {\n");
        html.append("            display: block;\n");
        html.append("            padding: 18px 24px;\n");
        html.append("            margin-bottom: 12px;\n");
        html.append("            background: #f7fafc;\n");
        html.append("            color: #404040;\n");
        html.append("            text-decoration: none;\n");
        html.append("            border-radius: 10px;\n");
        html.append("            border-left: 4px solid #989898;\n");
        html.append("            font-weight: 500;\n");
        html.append("            font-size: 1.1em;\n");
        html.append("            transition: all 0.2s;\n");
        html.append("        }\n");
        html.append("        .result-link:hover {\n");
        html.append("            background: #edf2f7;\n");
        html.append("            transform: translateX(5px);\n");
        html.append("        }\n");
        html.append("        .warning {\n");
        html.append("            color: #000000;\n");
        html.append("            font-size: 1.05em;\n");
        html.append("            line-height: 1.6;\n");
        html.append("        }\n");
        html.append("    </style>\n");
        html.append("</head>\n");
        html.append("<body>\n");
        html.append("    <div class='header'>\n");
        html.append("        <a href='/' class='back-link'>Back to Search</a>\n");
        html.append("        <h1>Search Results</h1>\n");
        html.append("        <p class='subtitle'>Alias Searched: \"").append(escapeHtml(query)).append("\"</p>\n");
        html.append("    </div>\n");
        html.append("    <div class='container'>\n");

        // If there is no result 
        if (results.isEmpty()) {
            html.append("        <div class='no-results'>\n");
            html.append("            <h2>No countries found...</h2>\n");
            html.append("            <p>Try searching for \"United States\", \"Germany\", or \"Japan\"</p>\n");
            html.append("        </div>\n");
        } else if (results.size() == 1) {
            // Single result - show full details
            Map<String, String> country = results.get(0);
            html.append("<div class='country-card'>\n");
            String shortName = country.getOrDefault("Country", "Unknown");
            String longName = country.getOrDefault("Government: Country name: conventional long form", "");
            String displayName = shortName;
            if (!longName.isEmpty() && !longName.equals(shortName)) {
                //displayName = shortName + " (" + longName + ")";
            }        
            displayName = shortName;
            html.append("<h2 class='country-name'>").append(escapeHtml(displayName)).append("</h2>\n");
            
            // Display main fields
            for (Map.Entry<String, String> field : DISPLAY_FIELDS.entrySet()) {
                if (field.getKey().equals("Country")) continue;
                String value = country.getOrDefault(field.getKey(), "").trim();
                if (!value.isEmpty()) {
                    if (field.equals("Official Name"))
                    {
                        if (value.equals("none"))
                        {
                            // These lines of code should fix the error where some alias's have a (none) written in the Offical Name.
                            System.out.println("Found country names that has a null value: " + field.getValue() + ". Fixing now!");
                            html.append("            <div class='field'>\n");
                            html.append("                <div class='field-label'>").append(escapeHtml(field.getValue())).append("</div>\n");
                            html.append("                <div class='field-value'>").append(escapeHtml(field.getValue())).append("</div>\n");
                            html.append("            </div>\n");
                        }
                    }
                    else
                    {
                        System.out.println("Found country name that does not have a null value: " + field.getValue() + ");
                        html.append("            <div class='field'>\n");
                        html.append("                <div class='field-label'>").append(escapeHtml(field.getValue())).append("</div>\n");
                        html.append("                <div class='field-value'>").append(escapeHtml(value)).append("</div>\n");
                        html.append("            </div>\n");   
                    }
                }
            }
            
            // Toggle button
            html.append("            <div class='toggle-container'>\n");
            html.append("                <button class='toggle-button' onclick='toggleAllData()' id='toggleBtn'>Show All Data</button>\n");
            html.append("            </div>\n");
            
            // All additional data (hidden by default)
            html.append("            <div class='all-data' id='allData'>\n");
            html.append("                <div class='all-data-header'>Complete Database Information</div>\n");
            
            // Display ALL fields from CSV
            for (Map.Entry<String, String> entry : country.entrySet()) {
                // Skip fields already shown in main display
                if (DISPLAY_FIELDS.containsKey(entry.getKey())) continue;
                
                String value = entry.getValue().trim();
                if (!value.isEmpty()) {
                    html.append("                <div class='field'>\n");
                    html.append("                    <div class='field-label'>").append(escapeHtml(entry.getKey())).append("</div>\n");
                    html.append("                    <div class='field-value'>").append(escapeHtml(value)).append("</div>\n");
                    html.append("                </div>\n");
                }
            }
            html.append("            <p class=\"warning\">Warning: Some information might be outdated or incorrect, please check statistics before using(most of the economical stats are correct, its mostly the Population stats that are incorrect). Some dates are listed but some are missed, if you have any issues please report it to the Github at <a href=\"https://github.com/CappiSRONT/web/issues\" target=\"_blank\">github.com</a>.</p>\n");
            html.append("            </div>\n");
            html.append("        </div>\n");
            
            // JavaScript for toggle
            // The acutal code for the toggle script
            html.append("    <script>\n");
            html.append("        function toggleAllData() {\n");
            html.append("            const allData = document.getElementById('allData');\n");
            html.append("            const btn = document.getElementById('toggleBtn');\n");
            html.append("            allData.classList.toggle('visible');\n");
            html.append("            if (allData.classList.contains('visible')) {\n");
            html.append("                btn.textContent = 'Hide All Data';\n");
            html.append("                btn.style.background = 'linear-gradient(135deg, #f56565 0%, #ac1010 100%)';\n");
            html.append("                btn.style.boxShadow = '0 8px 16px rgba(200, 1, 57, 0.3)';\n");
            html.append("            } else {\n");
            html.append("                btn.textContent = 'Show All Data';\n");
            html.append("                btn.style.background = 'linear-gradient(135deg, #48bb78 0%, #38a169 100%)';\n");
            html.append("                btn.style.boxShadow = '0 8px 16px rgba(72, 187, 120, 0.3)';\n");
            html.append("            }\n");
            html.append("        }\n");
            html.append("    </script>\n");
            
        } else {
            // Multiple results - show list
            html.append("<div class='multiple-results'>\n");
            html.append("<h2 style='margin-bottom: 20px;'>Found ").append(results.size()).append(" matches</h2>\n");
            for (Map<String, String> country : results) {
                String name = country.getOrDefault("Country", "Unknown");
                String longName = country.getOrDefault("Government: Country name: conventional long form", "");
                String displayName = name;
                if (!longName.isEmpty() && !longName.equals(name) && !longName.equals("none")) {
                    displayName = name + " (" + longName + ")";
                }
                else
                {
                    displayName = name;
                    System.out.println("Found country name: " + name);
                }
                String encoded = URLEncoder.encode(name, java.nio.charset.StandardCharsets.UTF_8);
                html.append("<a href='/search?q=").append(encoded).append("' class='result-link'>\n");
                html.append("    ").append(escapeHtml(displayName)).append("\n");
                html.append("</a>\n");
            }
            html.append("        </div>\n");
        }

        // THe spinny globe
        html.append("    </div>\n");
        html.append("    <script>\n");
        html.append("        const canvas = document.createElement('canvas');\n");
        html.append("        canvas.width = 64;\n");
        html.append("        canvas.height = 64;\n");
        html.append("        const ctx = canvas.getContext('2d');\n");
        html.append("        const globes = ['🌍', '🌎', '🌏'];\n");
        html.append("        let frame = 0;\n");
        html.append("        \n");
        html.append("        function updateFavicon() {\n");
        html.append("            ctx.clearRect(0, 0, 64, 64);\n");
        html.append("            ctx.font = '56px Arial';\n");
        html.append("            ctx.textAlign = 'center';\n");
        html.append("            ctx.textBaseline = 'middle';\n");
        html.append("            ctx.fillText(globes[frame % globes.length], 32, 36);\n");
        html.append("            \n");
        html.append("            const link = document.querySelector(\"link[rel*='icon']\") || document.createElement('link');\n");
        html.append("            link.type = 'image/x-icon';\n");
        html.append("            link.rel = 'shortcut icon';\n");
        html.append("            link.href = canvas.toDataURL();\n");
        html.append("            document.getElementsByTagName('head')[0].appendChild(link);\n");
        html.append("            \n");
        html.append("            frame++;\n");
        html.append("        }\n");
        html.append("        \n");
        html.append("        setInterval(updateFavicon, 500);\n");
        html.append("        updateFavicon();\n");
        html.append("    </script>\n");
        html.append("</body>\n");
        html.append("</html>");
        
        return html.toString();
    }

    private static List<Map<String, String>> searchCountry(String query) {
    String q = query.toLowerCase().trim();
    List<Map<String, String>> exactMatches = new ArrayList<>();
    List<Map<String, String>> partialMatches = new ArrayList<>();

    for (Map<String, String> row : countries) {
        String name = row.getOrDefault("Country", "").toLowerCase();
        String longName = row.getOrDefault("Government: Country name: conventional long form", "").toLowerCase();

        if (name.equals(q) || longName.equals(q)) {
            exactMatches.add(row);
        } else if (name.contains(q) || longName.contains(q)) {
            partialMatches.add(row);
        }
    }

        // If we found an exact match, return only that — don't mix in partials
    if (!exactMatches.isEmpty()) return exactMatches;
    return partialMatches;
        
    }

    // Everything below this point is for the CVS and JSON files.
    private static String resultsToJSON(List<Map<String, String>> results) {
        StringBuilder json = new StringBuilder("[");
        for (int i = 0; i < results.size(); i++) {
            if (i > 0) json.append(",");
            json.append("{");
            Map<String, String> country = results.get(i);
            boolean first = true;
            for (Map.Entry<String, String> entry : country.entrySet()) {
                if (!first) json.append(",");
                json.append("\"").append(escapeJson(entry.getKey())).append("\":\"")
                    .append(escapeJson(entry.getValue())).append("\"");
                first = false;
            }
            json.append("}");
        }
        json.append("]");
        return json.toString();
    }

    private static String escapeHtml(String str) {
        return str.replace("&", "&amp;")
                  .replace("<", "&lt;")
                  .replace(">", "&gt;")
                  .replace("\"", "&quot;")
                  .replace("'", "&#39;");
    }

    private static String escapeJson(String str) {
        return str.replace("\\", "\\\\")
                  .replace("\"", "\\\"")
                  .replace("\n", "\\n")
                  .replace("\r", "\\r")
                  .replace("\t", "\\t");
    }

    private static void loadCSV(String path) throws IOException {
        String content = Files.readString(Path.of(path), java.nio.charset.StandardCharsets.UTF_8);
        
        List<String> rawLines = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean inQuotes = false;

        for (int i = 0; i < content.length(); i++) {
            char c = content.charAt(i);
            if (c == '"') {
                inQuotes = !inQuotes;
                current.append(c);
            } else if ((c == '\n' || c == '\r') && !inQuotes) {
                if (c == '\r' && i + 1 < content.length() && content.charAt(i + 1) == '\n') {
                    i++;
                }
                rawLines.add(current.toString());
                current = new StringBuilder();
            } else {
                current.append(c);
            }
        }
        if (!current.isEmpty()) rawLines.add(current.toString());

        if (rawLines.isEmpty()) throw new IOException("CSV file is empty.");

        String[] headerCols = splitCSVLine(rawLines.get(0));
        headers = Arrays.asList(headerCols);

        for (int i = 1; i < rawLines.size(); i++) {
            if (rawLines.get(i).isBlank()) continue;
            String[] cols = splitCSVLine(rawLines.get(i));
            Map<String, String> row = new LinkedHashMap<>();
            for (int j = 0; j < headers.size(); j++) {
                row.put(headers.get(j), j < cols.length ? cols[j] : "");
            }
            countries.add(row);
        }
    }

    private static String[] splitCSVLine(String line) {
        List<String> fields = new ArrayList<>();
        StringBuilder field = new StringBuilder();
        boolean inQuotes = false;

        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (c == '"') {
                if (inQuotes && i + 1 < line.length() && line.charAt(i + 1) == '"') {
                    field.append('"');
                    i++;
                } else {
                    inQuotes = !inQuotes;
                }
            } else if (c == ',' && !inQuotes) {
                fields.add(field.toString());
                field = new StringBuilder();
            } else {
                field.append(c);
            }
        }
        fields.add(field.toString());
        return fields.toArray(new String[0]);
    }
}
