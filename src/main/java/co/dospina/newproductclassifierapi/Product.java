package co.dospina.newproductclassifierapi;

import jakarta.annotation.Nullable;
import java.util.Optional;

public class Product {

    private String brandCode;
    private String partNumber;
    private String currentClassification;
    private String currentClassificationDesc;
    @Nullable
    private String longDescription;
    private String shortDescription;
    private String manualNewTaxonomy;
    private String autoNewTaxonomy;

    public Product(String brandCode, String partNumber, String currentClassification, String currentClassificationDesc,
        String longDescription, String shortDescription, String manualNewTaxonomy) {
        this.brandCode = brandCode;
        this.partNumber = partNumber;
        this.currentClassification = currentClassification;
        this.currentClassificationDesc = currentClassificationDesc;
        this.longDescription = longDescription;
        this.shortDescription = shortDescription;
        this.manualNewTaxonomy = manualNewTaxonomy;
    }

    public String getBrandCode() {
        return brandCode;
    }

    public void setBrandCode(String brandCode) {
        this.brandCode = brandCode;
    }

    public String getPartNumber() {
        return partNumber;
    }

    public void setPartNumber(String partNumber) {
        this.partNumber = partNumber;
    }

    public String getCurrentClassification() {
        return currentClassification;
    }

    public void setCurrentClassification(String currentClassification) {
        this.currentClassification = currentClassification;
    }

    public String getCurrentClassificationDesc() {
        return currentClassificationDesc;
    }

    public void setCurrentClassificationDesc(String currentClassificationDesc) {
        this.currentClassificationDesc = currentClassificationDesc;
    }

    public Optional<String> getLongDescription() {
        return Optional.ofNullable(longDescription);
    }

    public void setLongDescription(String longDescription) {
        this.longDescription = longDescription;
    }

    public String getShortDescription() {
        return shortDescription;
    }

    public void setShortDescription(String shortDescription) {
        this.shortDescription = shortDescription;
    }

    public String getManualNewTaxonomy() {
        return manualNewTaxonomy;
    }

    public void setManualNewTaxonomy(String manualNewTaxonomy) {
        this.manualNewTaxonomy = manualNewTaxonomy;
    }

    public String getAutoNewTaxonomy() {
        return autoNewTaxonomy;
    }

    public void setAutoNewTaxonomy(String autoNewTaxonomy) {
        this.autoNewTaxonomy = autoNewTaxonomy;
    }
}
