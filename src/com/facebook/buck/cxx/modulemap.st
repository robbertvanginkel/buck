module <module_name> {
    <if(has_umbrella_header)>
    umbrella header "<module_name>.h"
    <else>
    umbrella "."

    <if(has_swift_header)>
    exclude header "<module_name>-Swift.h"
    <endif>
    <endif>

    export *
    module * { export * }
}

<if(has_swift_header)>
module <module_name>.Swift {
    header "<module_name>-Swift.h"
}
<endif>