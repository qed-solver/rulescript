package rulescript_test

import (
	"testing"

	"github.com/cockroachdb/cockroach/pkg/sql/opt"
	"github.com/cockroachdb/cockroach/pkg/sql/opt/memo"
)

func TestProjectRemove(t *testing.T) {
	rule := loadRule(t, "ProjectRemove")
	e := newEnv(t)
	input, cols := e.scan("u", 1)
	before := e.f.ConstructProject(input, memo.ProjectionsExpr{}, opt.MakeColSet(cols[0]))
	e.expectRewrite(rule, before, input)

	wide, wideCols := e.scan("t", 2)
	nearMiss := e.f.ConstructProject(wide, memo.ProjectionsExpr{}, opt.MakeColSet(wideCols[0]))
	e.expectNoMatch(rule, nearMiss)
}
