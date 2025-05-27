package asagiribeta.serverMarket.client

import net.fabricmc.fabric.api.datagen.v1.DataGeneratorEntrypoint
import net.fabricmc.fabric.api.datagen.v1.FabricDataGenerator

class ServerMarketDataGenerator : DataGeneratorEntrypoint {

    // 修复参数类型注解问题，修正参数语法
    override fun onInitializeDataGenerator(fabricDataGenerator: FabricDataGenerator) {
        val pack = fabricDataGenerator.createPack()
    }
}
