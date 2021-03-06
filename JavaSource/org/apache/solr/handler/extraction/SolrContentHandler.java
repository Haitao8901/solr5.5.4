/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.solr.handler.extraction;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.lang.invoke.MethodHandles;
import java.text.DateFormat;
import java.util.ArrayDeque;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.poi.EncryptedDocumentException;
import org.apache.poi.openxml4j.exceptions.InvalidFormatException;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellValue;
import org.apache.poi.ss.usermodel.FormulaEvaluator;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.apache.solr.common.SolrInputDocument;
import org.apache.solr.common.params.SolrParams;
import org.apache.solr.common.util.DateUtil;
import org.apache.solr.schema.IndexSchema;
import org.apache.solr.schema.SchemaField;
import org.apache.solr.schema.TrieDateField;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.TikaMetadataKeys;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xml.sax.Attributes;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;


/**
 * The class responsible for handling Tika events and translating them into {@link org.apache.solr.common.SolrInputDocument}s.
 * <B>This class is not thread-safe.</B>
 * <p>
 * This class cannot be reused, you have to create a new instance per document!
 * <p>
 * User's may wish to override this class to provide their own functionality.
 *
 * @see org.apache.solr.handler.extraction.SolrContentHandlerFactory
 * @see org.apache.solr.handler.extraction.ExtractingRequestHandler
 * @see org.apache.solr.handler.extraction.ExtractingDocumentLoader
 */
public class SolrContentHandler extends DefaultHandler implements ExtractingParams {
  private static final Logger log = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

  public static final String contentFieldName = "content";
  public static final String strcontentFieldName = "strcontent";

  protected final SolrInputDocument document;

  protected final Collection<String> dateFormats;

  protected final Metadata metadata;
  protected final SolrParams params;
  protected final StringBuilder catchAllBuilder = new StringBuilder(2048);
  protected final IndexSchema schema;
  protected final Map<String, StringBuilder> fieldBuilders;
  private final Deque<StringBuilder> bldrStack = new ArrayDeque<>();

  protected final boolean captureAttribs;
  protected final boolean lowerNames;
  
  protected final String unknownFieldPrefix;
  protected final String defaultField;

  private final boolean literalsOverride;
  
  private Set<String> literalFieldNames = null;
  
  public SolrContentHandler(Metadata metadata, SolrParams params, IndexSchema schema) {
    this(metadata, params, schema, DateUtil.DEFAULT_DATE_FORMATS);
  }


  public SolrContentHandler(Metadata metadata, SolrParams params,
                            IndexSchema schema, Collection<String> dateFormats) {
    this.document = new SolrInputDocument();
    this.metadata = metadata;
    this.params = params;
    this.schema = schema;
    this.dateFormats = dateFormats;

    this.lowerNames = params.getBool(LOWERNAMES, false);
    this.captureAttribs = params.getBool(CAPTURE_ATTRIBUTES, false);
    this.literalsOverride = params.getBool(LITERALS_OVERRIDE, true);
    this.unknownFieldPrefix = params.get(UNKNOWN_FIELD_PREFIX, "");
    this.defaultField = params.get(DEFAULT_FIELD, "");
    
    String[] captureFields = params.getParams(CAPTURE_ELEMENTS);
    if (captureFields != null && captureFields.length > 0) {
      fieldBuilders = new HashMap<>();
      for (int i = 0; i < captureFields.length; i++) {
        fieldBuilders.put(captureFields[i], new StringBuilder());
      }
    } else {
      fieldBuilders = Collections.emptyMap();
    }
    bldrStack.add(catchAllBuilder);
  }


  /**
   * This is called by a consumer when it is ready to deal with a new SolrInputDocument.  Overriding
   * classes can use this hook to add in or change whatever they deem fit for the document at that time.
   * The base implementation adds the metadata as fields, allowing for potential remapping.
   *
   * @return The {@link org.apache.solr.common.SolrInputDocument}.
   *
   * @see #addMetadata()
   * @see #addCapturedContent()
   * @see #addContent()
   * @see #addLiterals()
   */
  public SolrInputDocument newDocument() {
    //handle the literals from the params. NOTE: This MUST be called before the others in order for literals to override other values
    addLiterals();

    //handle the metadata extracted from the document
//    addMetadata();

    //add in the content
    addContent();

    //add in the captured content
    addCapturedContent();

    if (log.isDebugEnabled()) {
      log.debug("Doc: {}", document);
    }
    return document;
  }
  
  public SolrInputDocument newDocument(InputStream stream) throws Exception {
	    //handle the literals from the params. NOTE: This MUST be called before the others in order for literals to override other values
	    addLiterals();
	    String[] fileInfos = params.getParams(LITERALS_PREFIX + "fileinfo");
		Workbook wb = WorkbookFactory.create(stream);
		FormulaEvaluator evaluator= wb.getCreationHelper().createFormulaEvaluator();
		int i = wb.getNumberOfSheets() -1;
		while(i >= 0){
			String sheetName = wb.getSheetName(i);
			Sheet sheet = wb.getSheetAt(i);
			int rowNum = sheet.getLastRowNum() + 1;
			for(int j =0; j < rowNum; j++){
				Row row = sheet.getRow(j);
				if(row == null){
					//all line is empty
					continue;
				}
				for(int k =0; k <row.getLastCellNum(); k++){
					Cell cell = row.getCell(k);
					String value = null;
					try{
						value = this.getCellValue(cell, evaluator);
					}catch(Exception e){
						System.out.println("Get value failed for " + sheetName + ":" + i + "-" + j + "-" + k );
						System.out.println("Due to " + e.getMessage() + " just skip it.");
						e.printStackTrace();
					}
					
					if(value != null && !"".equals(value)) {
						SolrInputDocument doc = new SolrInputDocument();
						//i is sheetNum
						//j is rowNum
						//k is columnNum
						String id = i + "-" + j + "-" + k;
						
						doc.addField("debugid", params.get(LITERALS_PREFIX + "filepath") + "|||" + sheetName + "-" + id);
						doc.addField("id", params.get(LITERALS_PREFIX + "id") + "|||" + id);
						doc.addField("org", params.get(LITERALS_PREFIX + "org"));
						doc.addField("app", params.get(LITERALS_PREFIX + "app"));
						doc.addField("filepath", params.get(LITERALS_PREFIX + "filepath") + File.separator + "detail_text_info" + id);
						doc.addField("filename", params.get(LITERALS_PREFIX + "filename"));
						doc.addField("fileinfo", fileInfos!=null?fileInfos[0]:"");
						
						doc.addField("detailcontent", value);//detail used to show in gui
						doc.addField("content", value);
						document.addChildDocument(doc);
						System.out.println(sheetName + ":" + i + "-" + j + "-" + k + ">>" + value);
					}
				}
			}
			i--;
		}
	    addField(contentFieldName, "", null);
	    document.addField("debugid", "");
	    document.addField("detailcontent", "");
	    if (log.isDebugEnabled()) {
	      log.debug("Doc: {}", document);
	    }
	    return document;
	  }
  
  @SuppressWarnings("deprecation")
  private String getCellValue(Cell cell, FormulaEvaluator evaluator){
	  String value = null;
	  if(cell == null){
		  value = "";
	  }else if(cell.getCellType() == Cell.CELL_TYPE_NUMERIC){
		  value = String.valueOf(cell.getNumericCellValue());
	  }else if(cell.getCellType() == Cell.CELL_TYPE_FORMULA){
		  CellValue cellValue = evaluator.evaluate(cell);
		  value = getCellValue(cellValue);
	  }else{
		  try{
			  value = cell.getStringCellValue();
		  }catch(NumberFormatException e){
			  if(e.getMessage().equalsIgnoreCase("For input string: \"\"")){
				  value = "";
			  }else{
				  e.printStackTrace();
			  }
		  }
	  }
	  return value;
  }
  
  @SuppressWarnings("deprecation")
  private static String getCellValue(CellValue cell) {
      String cellValue = null;
      switch (cell.getCellType()) {
      case Cell.CELL_TYPE_STRING:
          cellValue=cell.getStringValue();
          break;
      case Cell.CELL_TYPE_NUMERIC:
          cellValue=String.valueOf(cell.getNumberValue());
          break;
      case Cell.CELL_TYPE_BOOLEAN:
    	  cellValue=String.valueOf(cell.getBooleanValue());
          break;
      default:
          break;
      }
      return cellValue;
  }

  /**
   * Add the per field captured content to the Solr Document.  Default implementation uses the
   * {@link #fieldBuilders} info
   */
  protected void addCapturedContent() {
    for (Map.Entry<String, StringBuilder> entry : fieldBuilders.entrySet()) {
      if (entry.getValue().length() > 0) {
        String fieldName = entry.getKey();
        if (literalsOverride && literalFieldNames.contains(fieldName))
          continue;
        addField(fieldName, entry.getValue().toString(), null);      }
    }
  }

  /**
   * Add in the catch all content to the field.  Default impl. uses the {@link #contentFieldName}
   * and the {@link #catchAllBuilder}
   */
  protected void addContent() {
    if (literalsOverride && literalFieldNames.contains(contentFieldName))
      return;
    
    //add by haitao, remove the meta data when add to document
    String contentStr = catchAllBuilder.toString();
    for (String name : metadata.names()) {
        String[] vals = metadata.getValues(name);
        for(int i =0; i < vals.length; i++){
        	String totalString = " " + name + " ";
        	totalString += vals[i] + "  \n";
        	contentStr = contentStr.replace(totalString, "");
        }
      }
//    addField(strcontentFieldName, contentStr, null);
    String[] fileInfos = params.getParams(LITERALS_PREFIX + "fileinfo");
    if(contentStr.indexOf("<mxGraphModel") >= 0){
    	String regs = "id=\"([^\"]+)\"[^>]+value=\"([^\"]+)\"";
    	Pattern pattern = Pattern.compile(regs);
    	Matcher matcher = pattern.matcher(contentStr);
    	while(matcher.find()){
    		SolrInputDocument doc = new SolrInputDocument();
    		String id = matcher.group(1);
    		String content = matcher.group(2);
    		
    		doc.addField("debugid", params.get(LITERALS_PREFIX + "filepath") + "#s1.89-o" + id);
    		doc.addField("id", params.get(LITERALS_PREFIX + "id") + "|||" + id);
    		doc.addField("org", params.get(LITERALS_PREFIX + "org"));
    		doc.addField("app", params.get(LITERALS_PREFIX + "app"));
    		doc.addField("filepath", params.get(LITERALS_PREFIX + "filepath") + File.separator + "detail_text_info" + id);
    		doc.addField("filename", params.get(LITERALS_PREFIX + "filename"));
    		doc.addField("fileinfo", fileInfos!=null?fileInfos[0]:"");
    		
    		//remove rex auto added tag
    		content = processSpecialCharacter(content);
    		doc.addField("detailcontent", content);//detail used to show in gui
    		doc.addField("content", content);
    		document.addChildDocument(doc);
    	}
    	//set content to empty, because all it's content has been moved to child document.
    	addField(contentFieldName, "", null);
    }else{
        addField(contentFieldName, contentStr.toString(), null);
    }
    //set debugid and detailcontent to empty
    document.addField("debugid", "");
    document.addField("detailcontent", "");
  }
  
  private String processSpecialCharacter(String str){
	  if(str == null || "".equals(str)){
		  return str;
	  }
	  // rex auto added tag is like this format 
	  // &lt;div&gt;aaaaaaa&lt;/&gt;
	  // user added tag is like below
	  // &amp;lt;div&amp;gt;aaaaaaa&amp;lt;/&amp;gt;
	  
	  //1. we need to remove the auto added tag first
	  //   transform   &lt; to <
	  //		       &gt; to >
	  //2. handle the user added tag
	  //   transform   &amp; to &
	  //		       &lt; to <
	  //		       &gt; to >
	  //		       &quot; to "
	  //               &nbsp; to ' '
	  //3. index the content
	  //4. GUI will handle  user added tag to make it show correctly
	  //   e.g. < to &lt;

	  str = str.replace("&lt;", "<").replace("&gt;", ">");
	  System.out.println(str);
	  //remove rex auto added tag.
	  str = str.replaceAll("<[^>]*>", "").replaceAll("</[^>]*>", "");
	  System.out.println(str);
	  //handle user added tag
	  str = str.replace("&amp;", "&").replace("&lt;", "<").replace("&gt;", ">").replace("&nbsp;", " ").replace("&quot;", "\"");
	  //replace <br> with empty string
	  str = str.replaceAll("<br>", "");
	  System.out.println(str);
	  return str;
  }

  /**
   * Add in the literals to the document using the {@link #params} and the {@link #LITERALS_PREFIX}.
   */
  protected void addLiterals() {
    Iterator<String> paramNames = params.getParameterNamesIterator();
    literalFieldNames = new HashSet<>();
    while (paramNames.hasNext()) {
      String pname = paramNames.next();
      if (!pname.startsWith(LITERALS_PREFIX)) continue;

      String name = pname.substring(LITERALS_PREFIX.length());
      addField(name, null, params.getParams(pname));
      literalFieldNames.add(name);
    }
  }

  /**
   * Add in any metadata using {@link #metadata} as the source.
   */
  protected void addMetadata() {
    for (String name : metadata.names()) {
      if (literalsOverride && literalFieldNames.contains(name))
        continue;
      String[] vals = metadata.getValues(name);
      addField(name, null, vals);
    }
  }

  // Naming rules:
  // 1) optionally map names to nicenames (lowercase+underscores)
  // 2) execute "map" commands
  // 3) if resulting field is unknown, map it to a common prefix
  protected void addField(String fname, String fval, String[] vals) {
    if (lowerNames) {
      StringBuilder sb = new StringBuilder();
      for (int i=0; i<fname.length(); i++) {
        char ch = fname.charAt(i);
        if (!Character.isLetterOrDigit(ch)) ch='_';
        else ch=Character.toLowerCase(ch);
        sb.append(ch);
      }
      fname = sb.toString();
    }    

    String name = findMappedName(fname);
    SchemaField sf = schema.getFieldOrNull(name);
    if (sf==null && unknownFieldPrefix.length() > 0) {
      name = unknownFieldPrefix + name;
      sf = schema.getFieldOrNull(name);
    } else if (sf == null && defaultField.length() > 0 && name.equals(TikaMetadataKeys.RESOURCE_NAME_KEY) == false /*let the fall through below handle this*/){
      name = defaultField;
      sf = schema.getFieldOrNull(name);
    }

    // Arguably we should handle this as a special case. Why? Because unlike basically
    // all the other fields in metadata, this one was probably set not by Tika by in
    // ExtractingDocumentLoader.load(). You shouldn't have to define a mapping for this
    // field just because you specified a resource.name parameter to the handler, should
    // you?
    if (sf == null && unknownFieldPrefix.length()==0 && name == TikaMetadataKeys.RESOURCE_NAME_KEY) {
      return;
    }

    // normalize val params so vals.length>1
    if (vals != null && vals.length==1) {
      fval = vals[0];
      vals = null;
    }

    // single valued field with multiple values... catenate them.
    if (sf != null && !sf.multiValued() && vals != null) {
      StringBuilder builder = new StringBuilder();
      boolean first=true;
      for (String val : vals) {
        if (first) {
          first=false;
        } else {
          builder.append(' ');
        }
        builder.append(val);
      }
      fval = builder.toString();
      vals=null;
    }

    float boost = getBoost(name);

    if (fval != null) {
      document.addField(name, transformValue(fval, sf), boost);
    }

    if (vals != null) {
      for (String val : vals) {
        document.addField(name, transformValue(val, sf), boost);
      }
    }

    // no value set - throw exception for debugging
    // if (vals==null && fval==null) throw new RuntimeException(name + " has no non-null value ");
  }

  @Override
  public void startElement(String uri, String localName, String qName, Attributes attributes) throws SAXException {
    StringBuilder theBldr = fieldBuilders.get(localName);
    if (theBldr != null) {
      //we need to switch the currentBuilder
      bldrStack.add(theBldr);
    }
    if (captureAttribs == true) {
      for (int i = 0; i < attributes.getLength(); i++) {
        addField(localName, attributes.getValue(i), null);
      }
    } else {
      for (int i = 0; i < attributes.getLength(); i++) {
        bldrStack.getLast().append(' ').append(attributes.getValue(i));
      }
    }
    bldrStack.getLast().append(' ');
  }

  @Override
  public void endElement(String uri, String localName, String qName) throws SAXException {
    StringBuilder theBldr = fieldBuilders.get(localName);
    if (theBldr != null) {
      //pop the stack
      bldrStack.removeLast();
      assert (bldrStack.size() >= 1);
    }
    bldrStack.getLast().append(' ');
  }


  @Override
  public void characters(char[] chars, int offset, int length) throws SAXException {
    bldrStack.getLast().append(chars, offset, length);
  }

  /**
   * Treat the same as any other characters
   */
  @Override
  public void ignorableWhitespace(char[] chars, int offset, int length) throws SAXException {
    characters(chars, offset, length);
  }

  /**
   * Can be used to transform input values based on their {@link org.apache.solr.schema.SchemaField}
   * <p>
   * This implementation only formats dates using the {@link org.apache.solr.common.util.DateUtil}.
   *
   * @param val    The value to transform
   * @param schFld The {@link org.apache.solr.schema.SchemaField}
   * @return The potentially new value.
   */
  protected String transformValue(String val, SchemaField schFld) {
    String result = val;
    if (schFld != null && schFld.getType() instanceof TrieDateField) {
      //try to transform the date
      try {
        Date date = DateUtil.parseDate(val, dateFormats);
        DateFormat df = DateUtil.getThreadLocalDateFormat();
        result = df.format(date);

      } catch (Exception e) {
        // Let the specific fieldType handle errors
        // throw new SolrException(SolrException.ErrorCode.SERVER_ERROR, "Invalid value: " + val + " for field: " + schFld, e);
      }
    }
    return result;
  }


  /**
   * Get the value of any boost factor for the mapped name.
   *
   * @param name The name of the field to see if there is a boost specified
   * @return The boost value
   */
  protected float getBoost(String name) {
    return params.getFloat(BOOST_PREFIX + name, 1.0f);
  }

  /**
   * Get the name mapping
   *
   * @param name The name to check to see if there is a mapping
   * @return The new name, if there is one, else <code>name</code>
   */
  protected String findMappedName(String name) {
    return params.get(MAP_PREFIX + name, name);
  }

}
