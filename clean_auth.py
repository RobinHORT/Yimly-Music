import re

with open('app/src/main/java/com/example/data/repository/AuthRepository.kt', 'r') as f:
    content = f.read()

content = re.sub(r'private suspend fun authenticateTestUser.*?_inMemorySession\.value = AuthSession\([^)]*\)\s*Result\.success\(testUser\)\s*\}', '', content, flags=re.DOTALL)

with open('app/src/main/java/com/example/data/repository/AuthRepository.kt', 'w') as f:
    f.write(content)
