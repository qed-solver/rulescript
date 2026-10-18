package rulescript_test

import (
	"testing"

	"github.com/cockroachdb/cockroach/pkg/sql/opt"
	"github.com/cockroachdb/cockroach/pkg/sql/opt/memo"
	"github.com/cockroachdb/cockroach/pkg/sql/types"
)

func TestProjectTableScan(t *testing.T) {
	e := newEnv(t)
	rule := loadRule(t, "ProjectTableScan")
	scan, cols := e.scan("t", 3)

	computed := e.f.Metadata().AddColumn("computed", types.Int)
	before := e.f.ConstructProject(scan, memo.ProjectionsExpr{
		e.f.ConstructProjectionsItem(e.f.ConstructPlus(e.v(cols[0]), e.v(cols[2])), computed),
	}, opt.MakeColSet(cols[2]))

	pushed := e.f.ConstructProject(scan, memo.ProjectionsExpr{}, opt.MakeColSet(cols[0], cols[2]))
	want := e.f.ConstructProject(pushed, memo.ProjectionsExpr{
		e.f.ConstructProjectionsItem(e.f.ConstructPlus(e.v(cols[0]), e.v(cols[2])), computed),
	}, opt.MakeColSet(cols[2]))
	e.expectRewrite(rule, before, want)

	nearMiss := e.f.ConstructProject(scan, memo.ProjectionsExpr{
		e.f.ConstructProjectionsItem(e.f.ConstructPlus(e.v(cols[0]), e.v(cols[1])), computed),
	}, opt.MakeColSet(cols[2]))
	e.expectNoMatch(rule, nearMiss)
}
