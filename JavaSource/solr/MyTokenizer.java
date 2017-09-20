package solr;

import java.io.IOException;

import org.apache.lucene.analysis.Tokenizer;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;
import org.apache.lucene.analysis.tokenattributes.OffsetAttribute;
import org.apache.lucene.analysis.util.CharacterUtils;
import org.apache.lucene.analysis.util.CharacterUtils.CharacterBuffer;
import org.apache.lucene.util.AttributeFactory;

/**
 * Because some of the important filed and methods of <class>CharTokenizer</class><br>
 * can not be override so here create this class.<br>
 * This class is main to make the MAX_WORD_LEN editable.
 * Default of CharTokenizer is 255 which may be a little small(because we use \n to  build token) 
 * @author haitao
 * */
public class MyTokenizer extends Tokenizer {
	  private int offset = 0, bufferIndex = 0, dataLen = 0, finalOffset = 0;
	  private int MAX_WORD_LEN = 0;
	  private int IO_BUFFER_SIZE = 0;
	  private final CharTermAttribute termAtt = addAttribute(CharTermAttribute.class);
	  private final OffsetAttribute offsetAtt = addAttribute(OffsetAttribute.class);
	  private final CharacterUtils charUtils = CharacterUtils.getInstance();
	  private final CharacterBuffer ioBuffer;
	  
	  
	  public MyTokenizer() {
		  this.ioBuffer = CharacterUtils.newCharacterBuffer(4096);
	  }
	  
	  public MyTokenizer(AttributeFactory factory, int wordlen, int buffersize) {
	    super(factory);
	    this.MAX_WORD_LEN = wordlen;
	    this.IO_BUFFER_SIZE = buffersize;
	    this.ioBuffer = CharacterUtils.newCharacterBuffer(IO_BUFFER_SIZE);
	  }
	  
	  
	  private boolean isTokenChar(int c) {
		//10 is \n    
	    return !(c==10);
	  }

	  /**
	   * Called on each token character to normalize it before it is added to the
	   * token. The default implementation does nothing. Subclasses may use this to,
	   * e.g., lowercase tokens.
	   */
	  protected int normalize(int c) {
	    return c;
	  }

	  @Override
	  public final boolean incrementToken() throws IOException {
	    clearAttributes();
	    int length = 0;
	    int start = -1; // this variable is always initialized
	    int end = -1;
	    char[] buffer = termAtt.buffer();
	    while (true) {
	      if (bufferIndex >= dataLen) {
	        offset += dataLen;
	        charUtils.fill(ioBuffer, input); // read supplementary char aware with CharacterUtils
	        if (ioBuffer.getLength() == 0) {
	          dataLen = 0; // so next offset += dataLen won't decrement offset
	          if (length > 0) {
	            break;
	          } else {
	            finalOffset = correctOffset(offset);
	            return false;
	          }
	        }
	        dataLen = ioBuffer.getLength();
	        bufferIndex = 0;
	      }
	      // use CharacterUtils here to support < 3.1 UTF-16 code unit behavior if the char based methods are gone
	      final int c = charUtils.codePointAt(ioBuffer.getBuffer(), bufferIndex, ioBuffer.getLength());
	      final int charCount = Character.charCount(c);
	      bufferIndex += charCount;

	      if (isTokenChar(c)) {               // if it's a token char
	        if (length == 0) {                // start of token
	          assert start == -1;
	          start = offset + bufferIndex - charCount;
	          end = start;
	        } else if (length >= buffer.length-1) { // check if a supplementary could run out of bounds
	          buffer = termAtt.resizeBuffer(2+length); // make sure a supplementary fits in the buffer
	        }
	        end += charCount;
	        length += Character.toChars(normalize(c), buffer, length); // buffer it, normalized
	        
	        if (MAX_WORD_LEN > 0 && length >= MAX_WORD_LEN) // buffer overflow! make sure to check for >= surrogate pair could break == test
	          break;
	      } else if (length > 0)             // at non-Letter w/ chars
	        break;                           // return 'em
	    }

	    termAtt.setLength(length);
	    assert start != -1;
	    offsetAtt.setOffset(correctOffset(start), finalOffset = correctOffset(end));
	    return true;
	    
	  }
	  
	  @Override
	  public final void end() throws IOException {
	    super.end();
	    // set final offset
	    offsetAtt.setOffset(finalOffset, finalOffset);
	  }

	  @Override
	  public void reset() throws IOException {
	    super.reset();
	    bufferIndex = 0;
	    offset = 0;
	    dataLen = 0;
	    finalOffset = 0;
	    ioBuffer.reset(); // make sure to reset the IO buffer!!
	  }
}
