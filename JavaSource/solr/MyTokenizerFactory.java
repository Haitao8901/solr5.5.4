package solr;

import java.util.Map;
import org.apache.lucene.analysis.Tokenizer;
import org.apache.lucene.analysis.util.TokenizerFactory;
import org.apache.lucene.util.AttributeFactory;

public class MyTokenizerFactory extends TokenizerFactory {
	  private final int maxTokenLength;
	  private final int bufferSize;
	  public MyTokenizerFactory(Map<String,String> args) {
	    super(args);
	    maxTokenLength = getInt(args, "maxTokenLength", 0);
	    bufferSize = getInt(args, "bufferSize", 4096);
	    if (!args.isEmpty()) {
	      throw new IllegalArgumentException("Unknown parameters: " + args);
	    }
	  }

	  @Override
	  public Tokenizer create(AttributeFactory factory) {
		  MyTokenizer tokenizer = new MyTokenizer(factory, maxTokenLength, bufferSize);
	      return tokenizer;
	  }
}
