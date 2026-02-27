package com.example.funder.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.MonetizationOn
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.funder.data.local.FundHoldingEntity
import com.example.funder.ui.theme.LossGreen
import com.example.funder.ui.theme.cardShape

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditHoldingDialog(
    holding: FundHoldingEntity,
    onDismiss: () -> Unit,
    onConfirm: (shares: Double, costPrice: Double) -> Unit
) {
    var shares by remember { mutableStateOf(holding.shares.toString()) }
    var costPrice by remember { mutableStateOf(holding.costPrice.toString()) }
    
    // 使用 derivedStateOf 优化验证逻辑
    val sharesError by remember {
        derivedStateOf {
            val value = shares.toDoubleOrNull()
            value == null || value <= 0
        }
    }
    
    val costPriceError by remember {
        derivedStateOf {
            val value = costPrice.toDoubleOrNull()
            value == null || value <= 0
        }
    }
    
    val totalCost by remember {
        derivedStateOf {
            (shares.toDoubleOrNull() ?: 0.0) * (costPrice.toDoubleOrNull() ?: 0.0)
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column {
                Text(
                    text = "编辑持仓",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = holding.fundName.ifEmpty { holding.fundCode },
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(18.dp)
            ) {
                // 基金代码
                Text(
                    text = "基金代码：${holding.fundCode}",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(vertical = 4.dp)
                )

                // 份额输入
                OutlinedTextField(
                    value = shares,
                    onValueChange = { shares = it },
                    label = { 
                        Text(
                            "持有份额",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium
                        ) 
                    },
                    placeholder = { Text("请输入持有份额") },
                    supportingText = {
                        if (sharesError) {
                            Text(
                                "请输入有效的份额（大于0）",
                                color = MaterialTheme.colorScheme.error,
                                fontSize = 12.sp
                            )
                        } else {
                            Text(
                                "单位：份",
                                color = MaterialTheme.colorScheme.onSurface,
                                fontSize = 12.sp
                            )
                        }
                    },
                    isError = sharesError,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outline
                    )
                )

                // 成本价输入
                OutlinedTextField(
                    value = costPrice,
                    onValueChange = { costPrice = it },
                    label = { 
                        Text(
                            "持仓成本",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium
                        ) 
                    },
                    placeholder = { Text("请输入持仓成本") },
                    supportingText = {
                        if (costPriceError) {
                            Text(
                                "请输入有效的成本（大于0）",
                                color = MaterialTheme.colorScheme.error,
                                fontSize = 12.sp
                            )
                        } else {
                            Text(
                                "单位：元/份",
                                color = MaterialTheme.colorScheme.onSurface,
                                fontSize = 12.sp
                            )
                        }
                    },
                    isError = costPriceError,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outline
                    )
                )

                // 计算的总成本卡片
                if (totalCost > 0) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = cardShape,
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer
                        )
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp)
                        ) {
                            Text(
                                text = "总成本",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurface,
                                fontWeight = FontWeight.Medium
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                text = "¥${"%.2f".format(totalCost)}",
                                fontSize = 20.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val sharesValue = shares.toDoubleOrNull()
                    val costPriceValue = costPrice.toDoubleOrNull()
                    
                    if (sharesValue != null && sharesValue > 0 &&
                        costPriceValue != null && costPriceValue > 0
                    ) {
                        onConfirm(sharesValue, costPriceValue)
                    }
                },
                enabled = !sharesError && !costPriceError &&
                        shares.toDoubleOrNull() != null && costPrice.toDoubleOrNull() != null,
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary
                ),
                modifier = Modifier.height(48.dp)
            ) {
                Text(
                    "确定",
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    letterSpacing = 0.5.sp
                )
            }
        },
        dismissButton = {
            OutlinedButton(
                onClick = onDismiss,
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                ),
                modifier = Modifier.height(48.dp)
            ) {
                Text(
                    "取消",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        },
        shape = cardShape,
        containerColor = MaterialTheme.colorScheme.surface
    )
}
