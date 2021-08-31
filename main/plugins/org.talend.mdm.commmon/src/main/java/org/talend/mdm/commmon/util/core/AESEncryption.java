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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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

	private static final Logger LOGGER = LoggerFactory.getLogger(AESEncryption.class);

	private static AESEncryption instance;

	public static final String KEYS_FILE = "encryption.keys.file"; //$NON-NLS-1$

	public static final String ENCRYPTION_KEY = "mdm.encryption.key"; //$NON-NLS-1$

	private Encryption encryption;

	public static AESEncryption getInstance() {
		if (instance == null) {
			instance = new AESEncryption();
		}
		return instance;
	}

	public AESEncryption() {
		try {
			String keyfile = System.getProperty(KEYS_FILE);
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
					if (StringUtils.isEmpty(config.getString(ENCRYPTION_KEY))) {
						config.setProperty(ENCRYPTION_KEY,
								EncodingUtils.BASE64_ENCODER.apply(KeySources.random(32).getKey()));
						config.save(file);
					}
				}
			}
			encryption = new Encryption(KeySources.file(ENCRYPTION_KEY), CipherSources.getDefault());
		} catch (Exception e) {
			LOGGER.error("Error occurs while initial AESEncryption. ", e);
		}
	}

	public String encrypt(String key, String value) throws IOException {
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
