package rulescript_test

import (
	"testing"

	"github.com/cockroachdb/cockroach/pkg/sql/opt"
	"github.com/cockroachdb/cockroach/pkg/sql/opt/memo"
)

func TestPruneExplainCols(t *testing.T) {
	rule := loadRule(t, "PruneExplainCols")
	e := newEnv(t)
	input, cols := e.scan("u", 2)
	out := e.f.Metadata().AddColumn("explain", e.v(cols[0]).DataType())
	before := e.f.ConstructProject(input, memo.ProjectionsExpr{e.f.ConstructProjectionsItem(e.v(cols[0]), out)}, opt.ColSet{})
	pruned := e.f.ConstructProject(input, nil, opt.MakeColSet(cols[0]))
	want := e.f.ConstructProject(pruned, memo.ProjectionsExpr{e.f.ConstructProjectionsItem(e.v(cols[0]), out)}, opt.ColSet{})
	e.expectRewrite(rule, before, want)

	nearMissOut := e.f.Metadata().AddColumn("explain", e.v(cols[1]).DataType())
	nearMiss := e.f.ConstructProject(input, memo.ProjectionsExpr{e.f.ConstructProjectionsItem(e.v(cols[1]), nearMissOut)}, opt.ColSet{})
	e.expectNoMatch(rule, nearMiss)
}
