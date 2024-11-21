# Enhanced Stock Analyzer - Formulae

This document lists the key formulae used in the Enhanced Stock Analyzer module for stock analysis and optimization.

---

## 1. Stock Cover  
Stock cover indicates how long the current stock will last based on recent demand.

$$
\text{Stock Cover} = \frac{\text{Current Stock}}{\text{Demand Rolling Mean}}
$$

---

## 2. Imbalance Score  
Imbalance score detects if the stock levels are too low or too high compared to the ideal range.

$$
\text{Imbalance Score} = 
\begin{cases} 
\text{Stock Cover} - \text{Lower Bound} & \text{if Stock Cover} < \text{Lower Bound} \\ 
\text{Stock Cover} - \text{Upper Bound} & \text{if Stock Cover} > \text{Upper Bound} \\ 
0 & \text{if Stock Cover is within the range} 
\end{cases}
$$

---

## 3. Stockout Risk  
Stockout risk is calculated based on stockouts, demand volatility, stock levels, and lead time.

### Formula:
$$
\text{Stockout Risk} = 
0.4 \cdot \frac{\text{Average Stockout Days}}{\text{Max Stockout Days}} 
+ 0.3 \cdot \frac{\text{Demand Rolling Std}}{\text{Demand Rolling Mean}} 
+ 0.2 \cdot \left(1 - \frac{\text{Current Stock}}{\text{Reorder Point}}\right) 
+ 0.1 \cdot e^{-\frac{1}{\text{Lead Time}}}
$$

### Percentage Representation:
$$
\text{Stockout Risk (\%)} = \min\left(\text{Stockout Risk} \cdot 100, 100\right)
$$

---

## 4. Demand Volatility  
Demand volatility measures fluctuations in demand and considers seasonal and promotional effects.

$$
\text{Demand Volatility} = \frac{\text{Demand Rolling Std}}{\text{Demand Rolling Mean}} 
\cdot \left(1 + 0.5 \cdot \text{Peak Season Factor}\right) 
\cdot \left(1 + 0.3 \cdot \text{Promotion Factor}\right)
$$

---

## 5. Economic Impact  
This is based on correlations between economic indicators and demand.

$$
\text{Economic Impact} = \frac{\text{Correlation with Consumer Confidence} - \text{Correlation with Unemployment Rate} - \text{Correlation with Inflation Rate}}{3}
$$

---

## 6. Seasonal Impact  
Seasonal impact analyzes seasonal demand patterns.

$$
\text{Seasonal Variation} = \frac{\text{Standard Deviation of Monthly Demand}}{\text{Mean Monthly Demand}}
$$

$$
\text{Peak Season Impact} = \frac{\text{Peak Season Demand}}{\text{Average Demand}}
$$

$$
\text{Seasonal Impact} = \text{Seasonal Variation} \cdot \text{Peak Season Impact}
$$

---

## 7. Reorder Point Effectiveness  
Evaluates how often stockouts occur after hitting the reorder point.

$$
\text{Reorder Point Effectiveness} = 1 - \frac{\text{Stockouts after hitting Reorder Point}}{\text{Total Reorder Point Hits}}
$$

---

## 8. Promotion Sensitivity  
Measures the change in demand during promotions.

$$
\text{Promotion Sensitivity} = \frac{\text{Promotion Demand}}{\text{Regular Demand}} - 1
$$

---

## 9. Holiday Impact  
Measures the change in demand during holidays.

$$
\text{Holiday Impact} = \frac{\text{Holiday Demand}}{\text{Regular Demand}} - 1
$$

---

## 10. Stock Efficiency  
Stock efficiency evaluates the balance between turnover, stockouts, and excess stock.

### Components:  
$\text{Turnover Rate} = \frac{\text{Total Demand}}{\text{Average Current Stock}}$
$\text{Excess Stock Rate} = \frac{\text{Count of Stock Above Max}}{\text{Total Stock Entries}}$

### Combined Formula:  
$$
\text{Stock Efficiency} = 
0.4 \cdot \min\left(\frac{\text{Turnover Rate}}{12}, 1\right) 
+ 0.3 \cdot (1 - \text{Stockout Rate}) 
+ 0.3 \cdot (1 - \text{Excess Stock Rate})
$$

---

These formulas guide the analysis of stock health and help generate insights into stock risks, efficiency, and optimization opportunities.
