

/* First created by JCasGen Sat Apr 30 11:35:10 CEST 2011 */
package de.unihd.dbs.uima.types.heideltime;

import org.apache.uima.jcas.JCas; 
import org.apache.uima.jcas.JCasRegistry;
import org.apache.uima.jcas.cas.TOP_Type;

import org.apache.uima.jcas.tcas.Annotation;


/** 
 * Updated by JCasGen Wed May 04 16:11:59 CEST 2011
 * XML source: /home/jstroetgen/workspace/heideltime-kit/desc/reader/ACETernReader.xml
 * @generated */
public class Token extends Annotation {
  /** @generated
   * @ordered 
   */
  public final static int typeIndexID = JCasRegistry.register(Token.class);
  /** @generated
   * @ordered 
   */
  public final static int type = typeIndexID;
  /** @generated  */
  public              int getTypeIndexID() {return typeIndexID;}
 
  /** Never called.  Disable default constructor
   * @generated */
  protected Token() {}
    
  /** Internal - constructor used by generator 
   * @generated */
  public Token(int addr, TOP_Type type) {
    super(addr, type);
    readObject();
  }
  
  /** @generated */
  public Token(JCas jcas) {
    super(jcas);
    readObject();   
  } 

  /** @generated */  
  public Token(JCas jcas, int begin, int end) {
    super(jcas);
    setBegin(begin);
    setEnd(end);
    readObject();
  }   

  /** <!-- begin-user-doc -->
    * Write your own initialization here
    * <!-- end-user-doc -->
  @generated modifiable */
  private void readObject() {}
     
 
    
  //*--------------*
  //* Feature: filename

  /** getter for filename - gets 
   * @generated */
  public String getFilename() {
    if (Token_Type.featOkTst && ((Token_Type)jcasType).casFeat_filename == null)
      jcasType.jcas.throwFeatMissing("filename", "de.unihd.dbs.uima.types.heideltime.Token");
    return jcasType.ll_cas.ll_getStringValue(addr, ((Token_Type)jcasType).casFeatCode_filename);}
    
  /** setter for filename - sets  
   * @generated */
  public void setFilename(String v) {
    if (Token_Type.featOkTst && ((Token_Type)jcasType).casFeat_filename == null)
      jcasType.jcas.throwFeatMissing("filename", "de.unihd.dbs.uima.types.heideltime.Token");
    jcasType.ll_cas.ll_setStringValue(addr, ((Token_Type)jcasType).casFeatCode_filename, v);}    
   
    
  //*--------------*
  //* Feature: tokenId

  /** getter for tokenId - gets 
   * @generated */
  public int getTokenId() {
    if (Token_Type.featOkTst && ((Token_Type)jcasType).casFeat_tokenId == null)
      jcasType.jcas.throwFeatMissing("tokenId", "de.unihd.dbs.uima.types.heideltime.Token");
    return jcasType.ll_cas.ll_getIntValue(addr, ((Token_Type)jcasType).casFeatCode_tokenId);}
    
  /** setter for tokenId - sets  
   * @generated */
  public void setTokenId(int v) {
    if (Token_Type.featOkTst && ((Token_Type)jcasType).casFeat_tokenId == null)
      jcasType.jcas.throwFeatMissing("tokenId", "de.unihd.dbs.uima.types.heideltime.Token");
    jcasType.ll_cas.ll_setIntValue(addr, ((Token_Type)jcasType).casFeatCode_tokenId, v);}    
   
    
  //*--------------*
  //* Feature: sentId

  /** getter for sentId - gets 
   * @generated */
  public int getSentId() {
    if (Token_Type.featOkTst && ((Token_Type)jcasType).casFeat_sentId == null)
      jcasType.jcas.throwFeatMissing("sentId", "de.unihd.dbs.uima.types.heideltime.Token");
    return jcasType.ll_cas.ll_getIntValue(addr, ((Token_Type)jcasType).casFeatCode_sentId);}
    
  /** setter for sentId - sets  
   * @generated */
  public void setSentId(int v) {
    if (Token_Type.featOkTst && ((Token_Type)jcasType).casFeat_sentId == null)
      jcasType.jcas.throwFeatMissing("sentId", "de.unihd.dbs.uima.types.heideltime.Token");
    jcasType.ll_cas.ll_setIntValue(addr, ((Token_Type)jcasType).casFeatCode_sentId, v);}    
   
    
  //*--------------*
  //* Feature: pos

  /** getter for pos - gets 
   * @generated */
  public String getPos() {
    if (Token_Type.featOkTst && ((Token_Type)jcasType).casFeat_pos == null)
      jcasType.jcas.throwFeatMissing("pos", "de.unihd.dbs.uima.types.heideltime.Token");
    return jcasType.ll_cas.ll_getStringValue(addr, ((Token_Type)jcasType).casFeatCode_pos);}
    
  /** setter for pos - sets  
   * @generated */
  public void setPos(String v) {
    if (Token_Type.featOkTst && ((Token_Type)jcasType).casFeat_pos == null)
      jcasType.jcas.throwFeatMissing("pos", "de.unihd.dbs.uima.types.heideltime.Token");
    jcasType.ll_cas.ll_setStringValue(addr, ((Token_Type)jcasType).casFeatCode_pos, v);}    
  }

    