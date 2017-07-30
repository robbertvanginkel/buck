module <module_name> {
    <if(has_umbrella_header)>
    umbrella header "<module_name>.h"

    export *
    module * { export * }
    <endif>
}

<if(has_swift_header)>
module <module_name>.Swift {
    header "<module_name>-Swift.h"
}
<endif>