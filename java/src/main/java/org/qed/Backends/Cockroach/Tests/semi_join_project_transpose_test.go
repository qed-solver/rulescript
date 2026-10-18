package rulescript_test

import (
	"testing"

	"github.com/cockroachdb/cockroach/pkg/sql/opt"
	"github.com/cockroachdb/cockroach/pkg/sql/opt/memo"
	"github.com/cockroachdb/cockroach/pkg/sql/types"
)

func TestSemiJoinProjectTranspose(t *testing.T) {
	rule := loadRule(t, "SemiJoinProjectTranspose")
	t.Run("transposes", func(t *testing.T) {
		e := newEnv(t)
		if _, err := e.cat.ExecuteDDL("CREATE TABLE bb (a BOOL, b BOOL)"); err != nil {
			t.Fatal(err)
		}
		x, xc := e.scan("bb", 2)
		y, yc := e.scan("bb", 2)
		p0 := e.f.ConstructProjectionsItem(e.f.ConstructEq(e.v(xc[0]), e.v(xc[1])), e.f.Metadata().AddColumn("p0", types.Bool))
		p1 := e.f.ConstructProjectionsItem(e.f.ConstructEq(e.v(xc[1]), e.v(xc[0])), e.f.Metadata().AddColumn("p1", types.Bool))
		project := e.f.ConstructProject(x, memo.ProjectionsExpr{p0, p1}, opt.ColSet{})
		cond := e.f.ConstructCoalesce(memo.ScalarListExpr{e.v(p0.Col), e.v(p1.Col), e.v(yc[0]), e.v(yc[1])})
		before := e.f.ConstructSemiJoin(project, y, e.filters(cond), &memo.JoinPrivate{})
		xl0 := e.v(xc[0])
		xl1 := e.v(xc[1])
		p0After := e.f.ConstructEq(xl0, xl1)
		p1After := e.f.ConstructEq(xl1, xl0)
		xy := e.f.ConstructSemiJoin(x, y, e.filters(e.f.ConstructCoalesce(memo.ScalarListExpr{p0After, p1After, e.v(yc[0]), e.v(yc[1])})), &memo.JoinPrivate{})
		want := e.f.ConstructProject(xy, memo.ProjectionsExpr{
			e.f.ConstructProjectionsItem(p0After, p0.Col),
			e.f.ConstructProjectionsItem(p1After, p1.Col),
		}, opt.ColSet{})
		e.expectRewrite(rule, before, want)
	})
	t.Run("near-miss", func(t *testing.T) {
		e := newEnv(t)
		if _, err := e.cat.ExecuteDDL("CREATE TABLE bb (a BOOL, b BOOL)"); err != nil {
			t.Fatal(err)
		}
		x, xc := e.scan("bb", 2)
		y, _ := e.scan("bb", 2)
		p0 := e.f.ConstructProjectionsItem(e.f.ConstructEq(e.v(xc[0]), e.v(xc[1])), e.f.Metadata().AddColumn("p0", types.Bool))
		project := e.f.ConstructProject(x, memo.ProjectionsExpr{p0}, opt.ColSet{})
		before := e.f.ConstructSemiJoin(project, y, e.filters(e.v(p0.Col)), &memo.JoinPrivate{})
		e.expectNoMatch(rule, before)
	})
}
