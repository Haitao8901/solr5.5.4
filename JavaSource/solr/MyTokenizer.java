package solr;

import org.apache.lucene.analysis.util.CharTokenizer;
import org.apache.lucene.util.AttributeFactory;

public class MyTokenizer extends CharTokenizer {
	  
	  public MyTokenizer() {
	  }

	  public MyTokenizer(AttributeFactory factory) {
	    super(factory);
	  }
	  
	  @Override
	  protected boolean isTokenChar(int c) {
		//10 is \n    
	    return !(c==10);
	  }

}
