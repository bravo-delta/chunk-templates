package com.x5.template;

import java.io.IOException;
import java.io.StringWriter;
import java.io.Writer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.x5.util.DataCapsuleTable;
import com.x5.util.TableData;

public class LoopTag extends BlockTag
{
    private TableData data;
    private Chunk chunk;
    private String rowTemplate;
    private String emptyTemplate;
    private Map<String,Object> options;
    
    private static final String ON_EMPTY_MARKER = "{~.onEmpty}";
    private static final String DIVIDER_MARKER = "{~.divider}";

    public static void main(String[] args)
    {
        String loopTest =
            "{~.loop data=\"~mydata\" template=\"#test_row\" no_data=\"#test_empty\"}";
        // test that the parser is not in and endless loop.
        LoopTag loop = new LoopTag();
        loop.parseParams(loopTest);
        System.out.println("row_tpl="+loop.rowTemplate);
        System.out.println("empty_tpl="+loop.emptyTemplate);
    }

    public static String expandLoop(String params, Chunk ch)
    throws BlockTagException
    {
        LoopTag loop = new LoopTag(params, ch);
        return loop._cookLoop();
    }

    public LoopTag()
    {
    }
    
    public LoopTag(String params, Chunk ch)
    {
        this.chunk = ch;
        parseParams(params);
    }

    public LoopTag(String params, Snippet body)
    {
        parseParams(params);
        initBody(body);
    }

    private void parseParams(String params)
    {
        if (params == null) return;

        if (params.startsWith(".loop(")) {
            parseFnParams(params);
        } else if (params.matches("\\.loop [^\" ]+ .*")) {
            parseEZParams(params);
        } else {
            parseAttributes(params);
        }
    }
    
    // {^loop in ~data}...{^/loop} (or {^loop in ~data as x}...)
    private void parseEZParams(String paramString)
    {
        String[] params = paramString.split(" +");
        
        String dataVar = params[2];
        fetchData(dataVar);
        
        this.options = _parseAttributes(paramString);
        if (options == null) options = new HashMap<String,Object>();
        options.put("data",dataVar);
        
        if (params.length > 3) {
            if (params[3].equals("as")) {
                options.put("name",params[4]);
            }
        }
    }

    // ^loop(~data[...range...],#rowTemplate,#emptyTemplate)
    private void parseFnParams(String params)
    {
        int endOfParams = params.length();
        if (params.endsWith(")")) endOfParams--;
        params = params.substring(".loop(".length(),endOfParams);
        String[] args = params.split(",");
        if (args != null && args.length >= 2) {
            String dataVar = args[0];
            fetchData(dataVar);

            this.rowTemplate = args[1];
            if (args.length > 2) {
                this.emptyTemplate = args[2];
            } else {
                this.emptyTemplate = null;
            }
        }
    }
    
    private static final Pattern PARAM_AND_VALUE = Pattern.compile(" ([a-zA-Z0-9_-]+)=(\"([^\"]*)\"|'([^\']*)')");
    
    private Map<String,Object> _parseAttributes(String params)
    {
        // find and save all xyz="abc" style attributes
        Matcher m = PARAM_AND_VALUE.matcher(params);
        HashMap<String,Object> opts = null;
        while (m.find()) {
            m.group(0); // need to do this for subsequent number to be correct?
            String paramName = m.group(1);
            String paramValue = m.group(3);
            if (opts == null) opts = new HashMap<String,Object>();
            opts.put(paramName, paramValue);
        }
        return opts;
    }
    
    // ^loop data="~data" template="#..." no_data="#..." range="..." per_page="x" page="x"
    private void parseAttributes(String params)
    {
        Map<String,Object> opts = _parseAttributes(params);
        
        if (opts == null) return;
        this.options = opts;
        
        String dataVar = (String)opts.get("data");
        fetchData(dataVar);
        
        this.rowTemplate = (String)opts.get("template");
        this.emptyTemplate = (String)opts.get("no_data");
        
        /*
        String dataVar = getAttribute("data", params);
        fetchData(dataVar);
        this.rowTemplate = getAttribute("template", params);
        this.emptyTemplate = getAttribute("no_data", params);

        // okay, this is heinously inefficient, scanning the whole thing every time for each param
        // esp. optional params which probably won't even be there
        String[] optional = new String[]{"range","divider","trim"}; //... what else?
        for (int i=0; i<optional.length; i++) {
            String param = optional[i];
            String val = getAttribute(param, params);
            if (val != null) registerOption(param, val);

            // really?
            if (param.equals("range") && val == null) {
                // alternate range specification via optional params page and per_page
                String perPage = getAttribute("per_page", params);
                String page = getAttribute("page", params);
                if (perPage != null && page != null) {
                    registerOption("range", page + "*" + perPage);
                }
            }
        }*/
    }

    private void fetchData(String dataVar)
    {
        if (dataVar != null) {
            int rangeMarker = dataVar.indexOf("[");
            if (rangeMarker > 0) {
                int rangeMarker2 = dataVar.indexOf("]",rangeMarker);
                if (rangeMarker2 < 0) rangeMarker2 = dataVar.length();
                String range = dataVar.substring(rangeMarker+1,rangeMarker2);
                dataVar = dataVar.substring(0,rangeMarker);
                registerOption("range",range);
            }
            if (dataVar.charAt(0) == '^') {
                // expand "external" shortcut syntax eg ^wiki becomes ~.wiki
                dataVar = TextFilter.applyRegex(dataVar, "s/^\\^/~./");
            }
            if (dataVar.startsWith("~")) {
                // tag reference (eg, tag assigned to query result table)
                dataVar = dataVar.substring(1);

                if (chunk != null) {
                    Object dataStore = chunk.get(dataVar);
                    if (dataStore instanceof TableData) {
                        this.data = (TableData)dataStore;
                    } else if (dataStore instanceof String) {
                        this.data = InlineTable.parseTable((String)dataStore);
                        ////registerOption("array_index_tags","FALSE");
                    } else if (dataStore instanceof Snippet) {
                        // simple strings are now encased in Snippet obj's
                        Snippet snippetData = (Snippet)dataStore;
                        this.data = InlineTable.parseTable(snippetData.toString());
                    } else if (dataStore instanceof String[]) {
                    	this.data = new SimpleTable((String[])dataStore);
                    } else if (dataStore instanceof Object[]) {
                    	// assume array of objects that implement DataCapsule
                    	this.data = DataCapsuleTable.extractData((Object[])dataStore);
                        ////registerOption("array_index_tags","FALSE");
                    }
                }
            } else {
                // template reference
                if (chunk != null) {
                	String tableAsString = chunk.getTemplateSet().fetch(dataVar);
                    this.data = InlineTable.parseTable(tableAsString);
                }
            }
        }
    }

    private void registerOption(String param, String value)
    {
        if (options == null) options = new java.util.HashMap<String,Object>();
        options.put(param,value);
    }

    private String _cookLoop()
    throws BlockTagException
    {
    	if (rowTemplate == null) throw new BlockTagException(this);
        return LoopTag.cookLoop(data, chunk, rowTemplate, emptyTemplate, options, false);
    }

    private static final Pattern NON_LEGAL = Pattern.compile("[^A-Za-z0-9_-]");
    
    public static String cookLoop(TableData data, Chunk context,
    		String rowTemplate, String emptyTemplate,
    		Map<String,Object> opt, boolean isBlock)
    {
        Snippet rowSnippet = null;
        Snippet emptySnippet = null;
        if (isBlock) {
            rowSnippet = rowTemplate == null ? null : new Snippet(rowTemplate);
            emptySnippet = emptyTemplate == null ? null : new Snippet(emptyTemplate);
        } else {
            if (rowTemplate != null) rowSnippet = context.getTemplateSet().getSnippet(rowTemplate);
            if (emptyTemplate != null) {
                if (emptyTemplate.isEmpty()) {
                    emptySnippet = new Snippet("");
                } else {
                    emptySnippet = context.getTemplateSet().getSnippet(emptyTemplate);
                }
            }
        }
        
        try {
            StringWriter out = new StringWriter();
            cookLoopToPrinter(out,data,context,rowSnippet,emptySnippet,opt,isBlock,1);
            out.flush();
            return out.toString();
        } catch (IOException e) {
            e.printStackTrace(System.err);
            return "[Loop error: IOException "+e.getLocalizedMessage()+"]";
        }
    }
     
    public static void cookLoopToPrinter(Writer out, TableData data, Chunk context,
            Snippet rowSnippet, Snippet emptySnippet,
            Map<String,Object> opt, boolean isBlock, int depth)
    throws IOException
    {
        if (data == null || !data.hasNext()) {
            if (emptySnippet == null) {
                if (isBlock) {
                    out.append("[Loop error: Empty Table - please supply ^onEmpty section in ^loop block]");
                } else {
                    out.append("[Loop Error: Empty Table - please specify no_data template parameter in ^loop tag]");
                }
            } else {
                emptySnippet.render(out, context, depth);
            }
            return;
        }
        
        Snippet dividerSnippet = null;
        boolean createArrayTags = true;
        
        if (opt != null) {
            if (opt.containsKey("dividerSnippet")) {
                dividerSnippet = (Snippet)opt.get("dividerSnippet");
            } else if (opt.containsKey("divider")) {
	        	String dividerTemplate = (String)opt.get("divider");
	        	ContentSource templates = context.getTemplateSet();
	        	if (templates.provides(dividerTemplate)) {
	        		dividerSnippet = templates.getSnippet(dividerTemplate);
	        	} else {
	        	    dividerSnippet = new Snippet(dividerTemplate);
	        	}
	        	opt.put("dividerSnippet", dividerSnippet);
        	}
        	if (opt.containsKey("array_index_tags")) {
        		createArrayTags = false;
        	}
        }

        ChunkFactory factory = context.getChunkFactory();

        String[] columnLabels = data.getColumnLabels();

        Chunk rowX;
        rowX = (factory == null) ? new Chunk() : factory.makeChunk();
        rowX.append( rowSnippet );

        String prefix = null;
        
        // set up all these auto-generated tags before entering the loop.
        String[] prefixedLabels = null;
        String[] prefixedIndices = null;
        String[] anonIndices = null;
        if (opt != null && opt.containsKey("name")) {
            String name = (String)opt.get("name");
            prefix = NON_LEGAL.matcher(name).replaceAll("");
            prefixedLabels = new String[columnLabels.length];
            for (int i=columnLabels.length-1; i>-1; i--) {
                prefixedLabels[i] = prefix + "." + columnLabels[i];
            }
            if (createArrayTags) {
                prefixedIndices = new String[columnLabels.length];   
                for (int i=0; i<prefixedIndices.length; i++) {
                    prefixedIndices[i] = prefix + "["+i+"]";
                }
            }
        }
        if (createArrayTags) {
            anonIndices = new String[columnLabels.length];   
            for (int i=0; i<anonIndices.length; i++) {
                anonIndices[i] = "DATA["+i+"]";
            }
        }
        
        int counter = 0;
        while (data.hasNext()) {
            rowX.set("0",counter);
            rowX.set("1",counter+1);
            
            if (dividerSnippet != null && counter > 0) {
                dividerSnippet.render(out, context, depth);
            }

            Map<String,String> record = data.nextRecord();

            // loop backwards -- in case any headers are identical,
            // this ensures the first such named column will be used
            for (int i=columnLabels.length-1; i>-1; i--) {
                String field = columnLabels[i];
                String value = record.get(field);
                // prefix with eg x. if prefix supplied
                String fieldName = prefix == null ? field : prefixedLabels[i];
                rowX.setOrDelete(fieldName, value);
                if (createArrayTags) {
                    rowX.setOrDelete(anonIndices[i], value);
                    if (prefix != null) {
                        rowX.setOrDelete(prefixedIndices[i], value);
                    }
                }
            }
            
            // for anonymous one-column tables (aka a string array)
            // allow loop in ~array as x to use {~x} for the value --
            // otherwise template has to have {~x[0]} or {~x.anonymous}
            // which is silly.
            if (prefix != null && columnLabels.length == 1 && columnLabels[0].equals(SimpleTable.ANON_ARRAY_LABEL)) {
                rowX.setOrDelete(prefix, record.get(SimpleTable.ANON_ARRAY_LABEL));
            }

            // make sure chunk tags are resolved in context
            rowX.render(out,context);

            counter++;
        }
        // no side effects!
        data.reset();

        //return rows.toString();
    }
    
    public boolean hasBody(String openingTag)
    {
        // loop has a body if there is no template="xxx" param
        if (openingTag != null && openingTag.indexOf("template=") < 0) {
            return true;
        } else {
            return false;
        }
    }
    
    public static String getAttribute(String attr, String toScan)
    {
        if (toScan == null) return null;

        // locate attributes
        int spacePos = toScan.indexOf(' ');

        // no attributes? (no spaces before >)
        if (spacePos < 0) return null;

        // pull out just the attribute definitions
        String attrs = toScan.substring(spacePos+1);

        // find our attribute
        int attrPos = attrs.indexOf(attr);
        if (attrPos < 0) return null;

        // find the equals sign
        int eqPos = attrs.indexOf('=',attrPos + attr.length());

        // find the opening quote
        int begQuotePos = attrs.indexOf('"',eqPos);
        if (begQuotePos < 0) return null;

        // find the closing quote
        int endQuotePos = begQuotePos+1;
        do {
            endQuotePos = attrs.indexOf('"',endQuotePos);
            if (endQuotePos < 0) return null;
            // FIXME this could get tripped up by escaped slash followed by unescaped quote
            if (attrs.charAt(endQuotePos-1) == '\\') {
                // escaped quote, doesn't count -- keep seeking
                endQuotePos++;
            }
        } while (endQuotePos < attrs.length() && attrs.charAt(endQuotePos) != '"');

        if (endQuotePos < attrs.length()) {
            return attrs.substring(begQuotePos+1,endQuotePos);
        } else {
            // never found closing quote
            return null;
        }
    }

    public String cookBlock(String blockBody)
    {
        // split body up into row template and optional empty template
        //  (delimited by {^on_empty} )
        // trim both, unless requested not to.
//        return null;
        boolean isBlock = true;
        
        boolean doTrim = true;
        String trimOpt = (options == null) ? null : (String)options.get("trim");
        if (trimOpt != null && trimOpt.equalsIgnoreCase("false")) {
            doTrim = false;
        }
        
        String divider = null;

        // how do we know these aren't inside a nested ^loop block?
        // find any nested blocks and only scan for these markers *outside* the
        // nested sections.
        int[] nestingGrounds = demarcateNestingGrounds(blockBody);
        
        int delimPos = blockBody.indexOf(ON_EMPTY_MARKER);
        int dividerPos = blockBody.indexOf(DIVIDER_MARKER);

        while (isInsideNestingGrounds(delimPos,nestingGrounds)) {
            delimPos = blockBody.indexOf(ON_EMPTY_MARKER,delimPos+ON_EMPTY_MARKER.length());
        }
        while (isInsideNestingGrounds(dividerPos,nestingGrounds)) {
            dividerPos = blockBody.indexOf(DIVIDER_MARKER,dividerPos+DIVIDER_MARKER.length());
        }
        
        if (dividerPos > -1) {
            if (delimPos > -1 && delimPos > dividerPos) {
                divider = blockBody.substring(dividerPos+DIVIDER_MARKER.length(),delimPos);
                // remove divider section from block body
                String before = blockBody.substring(0,dividerPos);
                String after = blockBody.substring(delimPos);
                blockBody = before + after;
                delimPos -= divider.length() + DIVIDER_MARKER.length();
            } else {
                divider = blockBody.substring(dividerPos+DIVIDER_MARKER.length());
                // remove divider section from block body
                blockBody = blockBody.substring(0,dividerPos);
            }
            divider = doTrim ? smartTrim(divider) : divider;
        }
        
        if (delimPos > -1) {
            String template = blockBody.substring(0,delimPos);
            String onEmpty = blockBody.substring(delimPos+ON_EMPTY_MARKER.length());
            this.rowTemplate = doTrim ? smartTrim(template) : template;
            this.emptyTemplate = doTrim ? onEmpty.trim() : onEmpty;
        } else {
            this.rowTemplate = doTrim ? smartTrim(blockBody) : blockBody;
        }
        
        if (divider != null) {
            registerOption("divider",divider);
        }
        
        return LoopTag.cookLoop(data, chunk, rowTemplate, emptyTemplate, options, isBlock);
    }
    
    private boolean isInsideNestingGrounds(int pos, int[] offLimits)
    {
        if (pos < 0) return false;
        if (offLimits == null) return false;
        
        for (int i=0; i<offLimits.length; i+=2) {
            int boundA = offLimits[i];
            int boundB = offLimits[i+1];
            if (pos < boundA) return false;
            if (pos < boundB) return true;
        }
        return false;
    }
    
    private int[] demarcateNestingGrounds(String blockBody)
    {
        String nestStart = this.chunk.tagStart + ".loop";
        int nestPos = blockBody.indexOf(nestStart);
        
        // easy case, no nesting
        if (nestPos < 0) return null;
        
        int[] bounds = null;
        while (nestPos > -1) {
            int[] endSpan = BlockTag.findMatchingBlockEnd(chunk, blockBody, nestPos+nestStart.length(), this);
            if (endSpan != null) {
                if (bounds == null) {
                    bounds = new int[]{nestPos,endSpan[1]};
                } else {
                    // grow each time -- yep, hugely inefficient,
                    // but hey, how often do we nest loops more than one deep?
                    int[] newBounds = new int[bounds.length+2];
                    System.arraycopy(bounds, 0, newBounds, 0, bounds.length);
                    newBounds[newBounds.length-2] = nestPos;
                    newBounds[newBounds.length-1] = endSpan[1];
                    bounds = newBounds;
                }
                nestPos = blockBody.indexOf(nestStart,endSpan[1]);
            } else {
                break;
            }
        }
        
        return bounds;
    }
    
    public String getBlockStartMarker()
    {
        return "loop";
    }
    
    public String getBlockEndMarker()
    {
        return "/loop";
    }
    
    private void smartTrim(List<SnippetPart> subParts)
    {
        if (subParts != null && subParts.size() > 0) {
            SnippetPart firstPart = subParts.get(0);
            if (firstPart.isLiteral()) {
                String trimmed = isTrimAll() ? trimLeft(firstPart.getText())
                        : smartTrim(firstPart.getText(), true);
                firstPart.setText(trimmed);
            }
            if (isTrimAll()) {
                SnippetPart lastPart = subParts.get(subParts.size()-1);
                if (lastPart.isLiteral()) {
                    String trimmed = trimRight(lastPart.getText());
                    lastPart.setText(trimmed);
                }
            }
        }
    }
    
    private String trimLeft(String x)
    {
        if (x == null) return null;
        int i = 0;
        char c = x.charAt(i);
        while (c == '\n' || c == ' ' || c == '\r' || c == '\t') {
            i++;
            if (i == x.length()) break;
            c = x.charAt(i);
        }
        if (i == 0) return x;
        return x.substring(i);
    }
    
    private String trimRight(String x)
    {
        if (x == null) return null;
        int i = x.length()-1;
        char c = x.charAt(i);
        while (c == '\n' || c == ' ' || c == '\r' || c == '\t') {
            i--;
            if (i == -1) break;
            c = x.charAt(i);
        }
        i++;
        if (i >= x.length()) return x;
        return x.substring(0,i);
    }
    
    private boolean isTrimAll()
    {
        String trimOpt = (options != null) ? (String)options.get("trim") : null;
        if (trimOpt != null && trimOpt.equals("all")) {
            return true;
        } else {
            return false;
        }
    }
    
    private String smartTrim(String x)
    {
        return smartTrim(x, false);
    }
    
    private static final Pattern UNIVERSAL_LF = Pattern.compile("\n|\r\n|\r\r");
    
    private String smartTrim(String x, boolean ignoreAll)
    {
        if (!ignoreAll && isTrimAll()) {
            // trim="all" disables smartTrim.
            return x.trim();
        }
        
        // if the block begins with (whitespace+) LF, trim initial line
        // otherwise, apply standard/complete trim.
        Matcher m = UNIVERSAL_LF.matcher(x);
        
        if (m.find()) {
            int firstLF = m.start();
            if (x.substring(0,firstLF).trim().length() == 0) {
                return x.substring(m.end());
            }
        }
        
        return ignoreAll ? x : x.trim();
        
        // if there were any line break chars at the end, add just one back.
        /*
        Pattern p = Pattern.compile(".*[ \\t]*(\\r\\n|\\n|\\r\\r)[ \\t]*$");
        Matcher m = p.matcher(x);
        if (m.find()) {
            m.group(0);
            String eol = m.group(1);
            return trimmed + eol;
        } else {
            return trimmed;
        }*/
    }

    private Snippet emptySnippet = null;
    private Snippet dividerSnippet = null;
    private Snippet rowSnippet = null;
    
    private void initBody(Snippet body)
    {
        // the snippet parts should already be properly nested,
        // so any ^onEmpty and ^divider tags at this level should
        // be for this loop.  locate and separate.
        
        int eMarker = -1, dMarker = -1;
        
        List<SnippetPart> bodyParts = body.getParts();
        for (int i=bodyParts.size()-1; i>=0; i--) {
            SnippetPart part = bodyParts.get(i);
            if (part.isTag()) {
                SnippetTag tag = (SnippetTag)part;
                if (tag.getTag().equals(".onEmpty")) {
                    eMarker = i;
                } else if (tag.getTag().equals(".divider")) {
                    dMarker = i;
                }
            }
        }
        
        boolean doTrim = true;
        String trimOpt = (options == null) ? null : (String)options.get("trim");
        if (trimOpt != null && trimOpt.equalsIgnoreCase("false")) {
            doTrim = false;
        }
        
        int eMarkerEnd, dMarkerEnd;
        
        int bodyEnd = -1;
        
        if (eMarker > -1 && dMarker > -1) {
            if (eMarker > dMarker) {
                bodyEnd = dMarker;
                eMarkerEnd = bodyParts.size();
                dMarkerEnd = eMarker;
            } else {
                bodyEnd = eMarker;
                eMarkerEnd = dMarker;
                dMarkerEnd = bodyParts.size();
            }
            emptySnippet = extractParts(bodyParts,eMarker+1,eMarkerEnd,doTrim);
            dividerSnippet = extractParts(bodyParts,dMarker+1,dMarkerEnd,doTrim);
        } else if (eMarker > -1) {
            bodyEnd = eMarker;
            eMarkerEnd = bodyParts.size();
            emptySnippet = extractParts(bodyParts,eMarker+1,eMarkerEnd,doTrim);
            dividerSnippet = null;
        } else if (dMarker > -1) {
            bodyEnd = dMarker;
            dMarkerEnd = bodyParts.size();
            emptySnippet = null;
            dividerSnippet = extractParts(bodyParts,dMarker+1,dMarkerEnd,doTrim);
        } else {
            emptySnippet = null;
            dividerSnippet = null;
        }
        
        if (bodyEnd > -1) {
            for (int i=bodyParts.size()-1; i>=bodyEnd; i--) {
                bodyParts.remove(i);
            }
        }
        
        if (doTrim) smartTrim(bodyParts);
        
        this.rowSnippet = body;
    }
    
    private Snippet extractParts(List<SnippetPart> parts, int a, int b, boolean doTrim)
    {
        List<SnippetPart> subParts = new ArrayList<SnippetPart>();
        for (int i=a; i<b; i++) {
            subParts.add(parts.get(i));
        }
        
        if (doTrim) smartTrim(subParts);
        
        return new Snippet(subParts);
    }
    
    @Override
    public void renderBlock(Writer out, Chunk context, int depth)
        throws IOException
    {
        if (dividerSnippet != null && !options.containsKey("dividerSnippet")) {
            options.put("dividerSnippet", dividerSnippet);
        }
        
        this.chunk = context;
        fetchData((String)options.get("data"));
        
        LoopTag.cookLoopToPrinter(out, data, context, rowSnippet, emptySnippet, options, true, depth);
    }

}