package rulescript_test

import (
	"testing"

	"github.com/cockroachdb/cockroach/pkg/sql/opt"
	"github.com/cockroachdb/cockroach/pkg/sql/opt/memo"
	"github.com/cockroachdb/cockroach/pkg/sql/sem/tree"
	"github.com/cockroachdb/cockroach/pkg/sql/types"
)

func TestTryDecorrelateProject(t *testing.T) {
	rule := loadRule(t, "TryDecorrelateProject")
	t.Run("rewrite", func(t *testing.T) {
		e := newEnv(t)
		l, lc := e.scan("u", 1)
		r, rc := e.scan("t", 2)
		projCols := opt.ColList{e.f.Metadata().AddColumn("p0", lcType(e, rc[1])), e.f.Metadata().AddColumn("p1", lcType(e, rc[0]))}
		projected := e.f.ConstructProject(r, memo.ProjectionsExpr{
			e.f.ConstructProjectionsItem(e.v(rc[1]), projCols[0]),
			e.f.ConstructProjectionsItem(e.v(rc[0]), projCols[1]),
		}, opt.ColSet{})
		pred := predicate(e, lc[0], projCols[0], projCols[1])
		before := e.f.ConstructInnerJoin(l, projected, memo.FiltersExpr{e.f.ConstructFiltersItem(pred)}, &memo.JoinPrivate{})
		wantJoin := e.f.ConstructInnerJoin(l, r, nil, &memo.JoinPrivate{})
		remapped := e.f.ConstructProject(wantJoin, nil, opt.MakeColSet(lc[0], rc[0], rc[1]))
		filtered := e.f.ConstructSelect(remapped, e.filters(predicate(e, lc[0], rc[1], rc[0])))
		want := e.f.ConstructProject(filtered, memo.ProjectionsExpr{
			e.f.ConstructProjectionsItem(e.v(rc[1]), projCols[0]),
			e.f.ConstructProjectionsItem(e.v(rc[0]), projCols[1]),
		}, opt.MakeColSet(lc[0]))
		e.expectRewrite(rule, before, want)
	})
	t.Run("near-miss", func(t *testing.T) {
		e := newEnv(t)
		l, _ := e.scan("u", 1)
		r, _ := e.scan("t", 2)
		e.expectNoMatch(rule, e.f.ConstructInnerJoin(l, r, nil, &memo.JoinPrivate{}))
	})
}

func predicate(e *env, a, b, c opt.ColumnID) opt.ScalarExpr {
	args := memo.ScalarListExpr{e.f.ConstructTuple(memo.ScalarListExpr{e.v(a), e.v(b), e.v(c)}, types.MakeTuple([]*types.T{types.Int, types.Int, types.Int}))}
	return e.f.ConstructFunction(args, &memo.FunctionPrivate{Name: "on", Typ: types.Bool, Properties: &tree.FunctionProperties{}, Overload: &tree.Overload{}})
}

func lcType(e *env, col opt.ColumnID) *types.T {
	return e.f.Metadata().ColumnMeta(col).Type
}
