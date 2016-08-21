import java.io.*;
import java.util.List;

import jeb.api.IScript;
import jeb.api.JebInstance;
import jeb.api.ui.*;
import jeb.api.ast.*;
import jeb.api.dex.Dex;
import jeb.api.dex.DexClass;
import jeb.api.dex.DexClassData;
import jeb.api.dex.DexMethod;
import jeb.api.dex.DexMethodData;

public class JebDecodeString implements IScript {
	private final static String DecodeMethodSignature = "Lcom/pnfsoftware/jebglobal/Si;->ob([BII)Ljava/lang/String;";
	private final static String DecodeClassSignature = "Lcom/pnfsoftware/jebglobal/Si;";
	private JebInstance mJebInstance = null;
	private Constant.Builder mBuilder = null;

	private static File logFile;
	private static BufferedWriter writer;

	/**
	 * ����: �������е��� �ҵ�ָ������
	 * 
	 * @return ָ�����dex����, û���ҵ�����-1
	 */
	@SuppressWarnings("unchecked")
	private int findClass(Dex dex, String findClassSignature) {
		List<String> listClassSignatures = dex.getClassSignatures(false);
		int index = 0;
		for (String classSignatures : listClassSignatures) {
			if (classSignatures.equals(findClassSignature)) {
				mJebInstance.print("find:" + classSignatures);
				return index;
			}
			index++;
		}

		return -1;
	}

	private int findMethod(Dex dex, int classIndex, String findMethodSignature) {
		DexClass dexClass = dex.getClass(classIndex);
		DexClassData dexClassData = dexClass.getData();
		DexMethodData[] dexMethods = dexClassData.getDirectMethods();
		for (int i = 0; i < dexMethods.length; i++) {
			int methodIndex = dexMethods[i].getMethodIndex();
			DexMethod dexMethod = dex.getMethod(methodIndex);
			String methodSignature = dexMethod.getSignature(true);

			if (methodSignature.equals(findMethodSignature)) {
				mJebInstance.print("find:" + methodSignature);
				return methodIndex;
			}
		}

		return -1;
	}
	
	/***
	 * ����: ����ָ��������Ӧ�÷���
	 * @param dex
	 * @param methodIndex
	 */
	@SuppressWarnings("unchecked")
	private void traverseReferences(Dex dex,int methodIndex) {
		List<Integer> methodReferences = dex.getMethodReferences(methodIndex);
		mJebInstance.print("��������:" + methodReferences.size());

		for (Integer refIndex : methodReferences) {
			DexMethod refDexMethod = dex.getMethod(refIndex);
			mJebInstance.print("���õķ�����" + refDexMethod.getSignature(true));

			// �ҵ�AST�ж�Ӧ��Method
			mJebInstance.decompileMethod(refDexMethod.getSignature(true));
			Method decompileMethodTree = mJebInstance.getDecompiledMethodTree(refDexMethod.getSignature(true));

			// �õ����飬�����������
			List<IElement> subElements = decompileMethodTree.getSubElements();
			replaceDecodeMethod(subElements, decompileMethodTree);
		}
	}

	@SuppressWarnings("unchecked")
	@Override
	public void run(JebInstance jebInstance) {
		// ��ʼ�������Ϣ
		jebInstance.print("start decode strings plugin");
		init(jebInstance, "D:\\log.txt");
		mBuilder = new Constant.Builder(jebInstance);
		JebUI ui = jebInstance.getUI();
		JavaView javaView = (JavaView) ui.getView(View.Type.JAVA);
		Dex dex = jebInstance.getDex();

		while (true) {
			int classIndex = findClass(dex, DecodeClassSignature);
			if (classIndex == -1) {
				break;
			}

			int methodIndex = findMethod(dex, classIndex, DecodeMethodSignature);
			if (methodIndex == -1) {
				break;
			}
			
			traverseReferences(dex,methodIndex);
			
			// ˢ��UI
			javaView.refresh();
			break;
		}

		// �ر��ļ�
		close();
	}
	
    private void replaceDecodeMethod(List<IElement> elements, IElement parentEle) {
        for (IElement element : elements) {
        	
        	if (!(element instanceof Call)) {
        		// ���Ƿ���
                List<IElement> subElements = element.getSubElements();
                replaceDecodeMethod(subElements, element);
        		continue;
        	}
        	
            Call call = (Call) element;
            Method method = call.getMethod();
            if (!method.getSignature().equals(DecodeMethodSignature)) {
            	// ����ָ������ǩ��
                List<IElement> subElements = element.getSubElements();
                replaceDecodeMethod(subElements, element);
            	continue;
            }
            
            
            analyzeArguments(call,parentEle,element);
            
        }
    }
    
    // �������ú����Ĳ���
    private void analyzeArguments(Call call,IElement parentEle,IElement element) {
        try {
        	// �õ������Ĳ���
            List<IExpression> arguments = call.getArguments();
            
            // ��ȡ��һ������Ԫ��
            NewArray arg1 = (NewArray) arguments.get(0);
            List encBL = arg1.getInitialValues();
            if (encBL == null) {
                return;
            }
            
            int size = encBL.size();
            byte[] enStrBytes = new byte[size];
            int decFlag;
            int encode;
            int i = 0;
            
            // ����Flags �еĵط������Ǳ�����ʽ�Ĳ���
            if (arguments.get(1) instanceof Constant) {
                decFlag = ((Constant) (arguments.get(1))).getInt();
            } else {
                decFlag = 4;
            }
            
            // ��ʼ�������ֽ�����
            for (i = 0; i < size; i++) {
                enStrBytes[i] = ((Constant) encBL.get(i)).getByte();
            }
            
            // ����encode
            encode = ((Constant) (arguments.get(2))).getInt();
            
            String decString = do_dec(enStrBytes,decFlag,encode);
            logWrite("���ܺ��ַ����� " + decString);
            // mJebInstance.print("���ܺ��ַ����� " + decString);
            
            // �滻ԭ���ı��ʽ
            parentEle.replaceSubElement(element, mBuilder.buildString(decString));
        } catch (Exception e) {
            mJebInstance.print(e.toString());
        }
    }
    
    // ������������ַ���
    private String do_dec(byte[] enStrBytes, int decFlag, int encode) {
        String dec = "";
        
        while (true) {
            if (decFlag != 4) {
                dec = decString(enStrBytes, decFlag, encode);
                break;
            }                   
            
            // ��ٿ��ܴ��ڵ���� 0 1 2
            dec = decString(enStrBytes, 2, encode);
            if (!isStr(dec)) {
                dec = decString(enStrBytes, 1, encode);
            }
            
            if (!isStr(dec)) {
                dec = decString(enStrBytes, 0, encode);
            }
        	break;
        }

        return dec;
    }
    
    // �ж��ַ����Ƿ���һ��������ַ���
    private boolean isStr(String s) {
        int len = s.length() > 3 ? 3 : s.length();
        String str = s.substring(0, len);
        if (str.matches("[a-zA-Z0-9_\u4e00-\u9fa5]*")) {
            return true;
        }
        return false;
    }
    
    private String setString(byte[] bytes_str) {
    	String new_str;
    	
        try {
            new_str = new String(bytes_str, "UTF-8");
        }
        catch(Exception e) {
            new_str = new String(bytes_str);
        }

        return new_str;
    }
    
    // �����ַ���
    public String decString(byte[] enStrBytes, int decFlag, int encode) {
        byte[] decstrArray;
        int enstrLen;

        if(enStrBytes == null) {
            return "decode error";
        }
        
        if (decFlag == 0 || enStrBytes.length == 0) {
        	return setString(enStrBytes);
        }
        
        if(decFlag == 1) {
            enstrLen = enStrBytes.length;
            decstrArray = new byte[enstrLen];
            byte bEncode = ((byte)encode);
            
            for (int i = 0;i < enstrLen;i++) {
            	decstrArray[i] = ((byte)(bEncode ^ enStrBytes[i]));
            	bEncode = decstrArray[i];
            }

            return setString(decstrArray);
        }
        
        if(decFlag == 2) {
            enstrLen = enStrBytes.length;
            decstrArray = new byte[enstrLen];
            String coprightString = "Copyright (c) 1993, 2015, Oracle and/or its affiliates. All rights reserved. ";
            int index = 0;
            for (int i = 0;i < enstrLen;i++) {
            	decstrArray[i] = ((byte)(enStrBytes[i] ^ (((byte)coprightString.charAt(index)))));
                index = (index + 1) % coprightString.length();
            }

            return setString(decstrArray);
        }
        
        return "decode error";
    }
	

	public void logWrite(String log) {
		try {
			writer.write(log + "\r\n");
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	public void init(JebInstance jebInstance, String logPath) {
		mJebInstance = jebInstance;
		logFile = new File(logPath);
		try {
			writer = new BufferedWriter(new OutputStreamWriter(
					new FileOutputStream(logFile), "utf-8"));
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	public void close() {
		try {
			writer.close();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
}
