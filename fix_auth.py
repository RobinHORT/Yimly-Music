import re

with open('app/src/main/java/com/example/data/repository/AuthRepository.kt', 'r') as f:
    content = f.read()

content = re.sub(r'if \(username\.trim\(\)\.equals\("test", ignoreCase = true\)\) \{\s*return@withContext authenticateTestUser\(rememberMe\)\s*\}', '', content)

with open('app/src/main/java/com/example/data/repository/AuthRepository.kt', 'w') as f:
    f.write(content)
