package com.example.demo;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.jms.annotation.JmsListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.amazonaws.auth.*;
import com.amazonaws.services.s3.*;
import com.amazonaws.services.s3.model.*;

import java.io.*;
import java.security.*;
import java.security.cert.*;
import java.util.*;

@Component
public class CertificateChecker {

    @Value("${aws.accessKey}")
    private String accessKey;
    @Value("${aws.secretKey}")
    private String secretKey;
    @Value("${aws.region}")
    private String region;
    @Value("${aws.bucket}")
    private String bucketName;

    @Value("${keystore.fileName}")
    private String keystoreFileName;
    @Value("${keystore.password}")
    private String keystorePassword;
    @Value("${keystore.path}")
    private String keystorePath;

    @Scheduled(cron = "0 0 0 * * ?")
    public void scheduledCheck() {
        System.out.println("Scheduled certificate check triggered...");
        triggerCertificateCheck();
    }

    @JmsListener(destination = "${ems.queueName}")
    public void receiveJmsTrigger(String message) {
        System.out.println("EMS trigger received: " + message);
        triggerCertificateCheck();
    }

    private void triggerCertificateCheck() {
        InputStream keystoreStream = loadKeystoreFromS3(bucketName, keystoreFileName);
        if (keystoreStream == null) {
            System.out.println("Loading keystore from local resources...");
            keystoreStream = getClass().getClassLoader().getResourceAsStream(keystoreFileName);
            if (keystoreStream == null) {
                System.out.println("Keystore not found.");
                return;
            }
        }
        processKeystore(keystoreStream);
    }

    private void processKeystore(InputStream keystoreStream) {
        try {
            KeyStore keyStore = KeyStore.getInstance("JKS");
            keyStore.load(keystoreStream, keystorePassword.toCharArray());

            Enumeration<String> aliases = keyStore.aliases();
            while (aliases.hasMoreElements()) {
                String alias = aliases.nextElement();
                X509Certificate cert = (X509Certificate) keyStore.getCertificate(alias);

                System.out.println("Alias: " + alias);
                System.out.println("Valid until: " + cert.getNotAfter());

                long daysLeft = (cert.getNotAfter().getTime() - System.currentTimeMillis()) / (1000 * 60 * 60 * 24);
                System.out.println("Days remaining: " + daysLeft);

                if (daysLeft < 3) {
                    replaceCertificate(alias);
                }
                System.out.println("------------------------");
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void replaceCertificate(String alias) {
        System.out.println("Replacing certificate for alias: " + alias);
        try {
            CertificateFactory cf = CertificateFactory.getInstance("X.509");
            InputStream newCertStream = getClass().getClassLoader().getResourceAsStream("new_certificate.crt");
            if (newCertStream == null) {
                System.out.println("New certificate not found.");
                return;
            }
            X509Certificate newCert = (X509Certificate) cf.generateCertificate(newCertStream);

            KeyStore keyStore = KeyStore.getInstance("JKS");
            try (FileInputStream fis = new FileInputStream(keystorePath)) {
                keyStore.load(fis, keystorePassword.toCharArray());
            }

            keyStore.setCertificateEntry(alias, newCert);

            try (FileOutputStream fos = new FileOutputStream(keystorePath)) {
                keyStore.store(fos, keystorePassword.toCharArray());
            }

            System.out.println("Certificate for alias '" + alias + "' replaced successfully.");

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private InputStream loadKeystoreFromS3(String bucket, String key) {
        try {
            AmazonS3 s3 = AmazonS3ClientBuilder.standard()
                    .withCredentials(new AWSStaticCredentialsProvider(new BasicAWSCredentials(accessKey, secretKey)))
                    .withRegion(region).build();
            return s3.getObject(bucket, key).getObjectContent();
        } catch (Exception e) {
            System.out.println("Could not load keystore from S3: " + e.getMessage());
            return null;
        }
    }
}
