<%
    /**
     * Copyright 2016 Sean Kavanagh - sean.p.kavanagh6@gmail.com
     *
     * Licensed under the Apache License, Version 2.0 (the "License");
     * you may not use this file except in compliance with the License.
     * You may obtain a copy of the License at
     *
     * http://www.apache.org/licenses/LICENSE-2.0
     *
     * Unless required by applicable law or agreed to in writing, software
     * distributed under the License is distributed on an "AS IS" BASIS,
     * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
     * See the License for the specific language governing permissions and
     * limitations under the License.
     */
%>
<%@ taglib prefix="s" uri="/struts-tags" %>
<!DOCTYPE html>
<html>
<head>

    <jsp:include page="../_res/inc/header.jsp"/>

    <script type="text/javascript">

        $(document).ready(function() {
            $("#create_btn").button().click(function() {
                $('#saveVM').submit();
            });
        });
    </script>

    <title>KeyBox - Create OpenStack VM</title>

</head>
<body>


<jsp:include page="../_res/inc/navigation.jsp"/>

<div class="container">

    <h3>KeyBox - Create VM</h3>

    <p>Create OpenStack VM and add to profile.</p>

    <s:actionerror escape="false"/>
    <s:form action="saveVM">
        <s:textfield name="openStackVM.name" label="Name"/>
        <s:select name="openStackVM.image" label="Image" list="imageMap"/>
        <s:select name="openStackVM.flavor" label="Size" list="flavorMap"/>
        <s:select name="openStackVM.network" label="Network" list="networkMap" multiple="true"/>
        <s:select name="openStackVM.securityGroup" label="Security Group" list="securityList"/>
        <tr> <td>&nbsp;</td>
            <td align="right">  <div id="create_btn" class="btn btn-default" >Create</div></td>
        </tr>
    </s:form>

</div>

</body>
</html>
