package rulescript_test

import (
	"testing"

	"github.com/cockroachdb/cockroach/pkg/sql/opt"
	"github.com/cockroachdb/cockroach/pkg/sql/opt/memo"
	"github.com/cockroachdb/cockroach/pkg/sql/types"
)

func TestProjectMerge(t *testing.T) {
	e := newEnv(t)
	rule := loadRule(t, "ProjectMerge")
	scan, cols := e.scan("t", 2)
	bottomCol := e.f.Metadata().AddColumn("bottom", types.Int)
	topCol := e.f.Metadata().AddColumn("top", types.Int)
	inner := e.f.ConstructProject(scan, memo.ProjectionsExpr{e.f.ConstructProjectionsItem(e.v(cols[0]), bottomCol)}, opt.ColSet{})
	before := e.f.ConstructProject(inner, memo.ProjectionsExpr{e.f.ConstructProjectionsItem(e.v(bottomCol), topCol)}, opt.ColSet{})
	want := e.f.ConstructProject(scan, memo.ProjectionsExpr{e.f.ConstructProjectionsItem(e.v(cols[0]), topCol)}, opt.ColSet{})
	e.expectRewrite(rule, before, want)

	nearMiss := e.f.ConstructProject(scan, memo.ProjectionsExpr{e.f.ConstructProjectionsItem(e.v(cols[0]), topCol)}, opt.ColSet{})
	e.expectNoMatch(rule, nearMiss)
}
