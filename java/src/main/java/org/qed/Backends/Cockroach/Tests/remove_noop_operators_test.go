package rulescript_test

import (
	"testing"

	"github.com/cockroachdb/cockroach/pkg/sql/opt"
	"github.com/cockroachdb/cockroach/pkg/sql/opt/memo"
)

func TestRemoveNoopOperators(t *testing.T) {
	rule := loadRule(t, "RemoveNoopOperators")
	e := newEnv(t)

	scan, cols := e.scan("t", 1)
	before := e.f.ConstructProject(scan, memo.ProjectionsExpr{}, opt.MakeColSet(cols...))
	e.expectRewrite(rule, before, scan)

	wideScan, wideCols := e.scan("t", 2)
	nearMiss := e.f.ConstructProject(wideScan, memo.ProjectionsExpr{}, opt.MakeColSet(wideCols...))
	e.expectNoMatch(rule, nearMiss)
}
