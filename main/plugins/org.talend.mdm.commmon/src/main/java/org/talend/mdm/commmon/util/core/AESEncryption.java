/*
 * Copyright (C) 2006-2021 Talend Inc. - www.talend.com
 *
 * This source code is available under agreement available at
 * %InstallDIR%\features\org.talend.rcp.branding.%PRODUCTNAME%\%PRODUCTNAME%license.txt
 *
 * You should have received a copy of the agreement along with this program; if not, write to Talend SA 9 rue Pages
 * 92150 Suresnes, France
 */
package org.talend.mdm.commmon.util.core;

import java.io.File;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.nio.charset.StandardCharsets;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;

import javax.crypto.Cipher;
import javax.crypto.ShortBufferException;
import javax.crypto.spec.SecretKeySpec;

import org.apache.commons.configuration.PropertiesConfiguration;
import org.talend.daikon.crypto.CipherSources;
import org.talend.daikon.crypto.EncodingUtils;
import org.talend.daikon.crypto.Encryption;
import org.talend.daikon.crypto.KeySource;
import org.talend.daikon.crypto.KeySources;

import org.apache.commons.lang.StringUtils;

public class AESEncryption {

	private static final Logger LOGGER = LogManager.getLogger(AESEncryption.class);

	private static AESEncryption instance;

	public static final String KEYS_FILE = "encryption.keys.file"; //$NON-NLS-1$

	private static final String AES_KEY = "aes.key"; //$NON-NLS-1$

	private Encryption encryption;

	private SecretKeySpec keySpec;

	private byte[] key;

	private String algorithm;

	public static AESEncryption getInstance() {
		if (instance == null) {
			instance = new AESEncryption();
		}
		return instance;
	}

	public AESEncryption() {
		try {
			String keyfile = System.getProperty(KEYS_FILE);
			KeySource keySource = null;
			if (StringUtils.isEmpty(keyfile)) {
				keyfile = System.getProperty("mdm.root") + System.getProperty("file.separator") + "conf"
						+ System.getProperty("file.separator") + "aeskey.dat";
				System.setProperty(KEYS_FILE, keyfile);
				File file = new File(keyfile);
				if (file.exists()) {
					PropertiesConfiguration config = new PropertiesConfiguration();
					config.setDelimiterParsingDisabled(true);
					config.setEncoding(StandardCharsets.UTF_8.name());
					config.load(file);
					if (StringUtils.isEmpty(config.getString(AES_KEY))) {
						keySource = KeySources.random(32);
						config.setProperty(AES_KEY, EncodingUtils.BASE64_ENCODER.apply(keySource.getKey()));
						config.save(file);
					}
				}
			}
			if (keySource == null) {
				keySource = KeySources.file(AES_KEY);
			}
			encryption = new Encryption(keySource, CipherSources.getDefault());
		} catch (Exception e) {
			LOGGER.error("Error occurs while initial AESEncryption. ", e);
		}
	}

	protected String encrypt(String key, String value) throws IOException {
		try {
			return StringUtils.isEmpty(value) ? StringUtils.EMPTY : encryption.encrypt(value);
		} catch (Exception e) {
			throw new IOException("Unable to encrypt value for key " + key, e);
		}
	}

	public String reEncrypt(String key, String value) throws IOException {
		return encrypt(key, decrypt(key, value));
	}

	public String decrypt(String key, String value) {
		try {
			return StringUtils.isEmpty(value) ? StringUtils.EMPTY : encryption.decrypt(value);
		} catch (Exception e) {
			// assuming it was not encrypted or encrypted with a different encryption
			LOGGER.debug("Unable to decrypt value using {} encryption..", encryption.getClass().getName(), e);
			return value;
		}
	}

	public static AESEncryption getDESCryptInstance(String sharedSecret) throws ShortBufferException {
		byte[] key = new byte[8];
		byte[] bytes;
		try {
			bytes = sharedSecret.getBytes(StandardCharsets.UTF_8.name());
		} catch (UnsupportedEncodingException uee) {
			throw new ShortBufferException("The shared secret cannot be used as a key");
		}
		if (bytes.length < 8)
			throw new ShortBufferException("The shared secret is too short");
		System.arraycopy(bytes, 0, key, 0, 8);
		return new AESEncryption(key, "DES");
	}

	/** Creates a new instance of Crypt */
	public AESEncryption(byte[] key, String algorithm) {
		this.key = key;
		this.algorithm = algorithm;
		this.keySpec = new SecretKeySpec(this.key, this.algorithm);
	}

	/**
	 * Encrypts the given String to a hex representation
	 */
	public String encryptHexString(String text) {
		return toHex(encryptString(text));
	}

	/** Converts the given array of bytes to a hex String */
	public static String toHex(byte[] buf) {
		char[] cbf = new char[buf.length * 2];
		for (int jj = 0, kk = 0; jj < buf.length; jj++) {
			cbf[kk++] = "0123456789ABCDEF".charAt((buf[jj] >> 4) & 0x0F);
			cbf[kk++] = "0123456789ABCDEF".charAt(buf[jj] & 0x0F);
		}
		return new String(cbf);
	}

	/** Encrypts the give String to an array of bytes */
	private byte[] encryptString(String text) {
		try {
			Cipher cipher = Cipher.getInstance(this.algorithm);
			cipher.init(Cipher.ENCRYPT_MODE, this.keySpec);
			return cipher.doFinal(text.getBytes(StandardCharsets.UTF_8));
		} catch (Exception e) {
			return null;
		}
	}

	public static void main(String[] args) {
		if (args.length < 2) {
			System.out.println("Parameters of 'original password' and 'AES encryption key' is required."); //$NON-NLS-1$
			return;
		}
		final String key = args[1];
		try {
			KeySource source = new KeySource() {

				private byte[] keyBytes = EncodingUtils.BASE64_DECODER.apply(key.getBytes(EncodingUtils.ENCODING));

				@Override
				public synchronized byte[] getKey() {
					return keyBytes;
				}
			};

			Encryption encryptionTool = new Encryption(source, CipherSources.getDefault());
			System.out.println(encryptionTool.encrypt(args[0]));
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
}
